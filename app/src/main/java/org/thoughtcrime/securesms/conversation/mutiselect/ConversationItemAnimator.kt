package org.thoughtcrime.securesms.conversation.mutiselect

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.RecyclerView
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.ConversationAdapter

/**
 * Class for managing the triggering of item animations (here in the form of decoration redraws) whenever
 * there is a "selection" edge detected.
 *
 * Also animates newly appearing messages: a vertical settle for most items, and a scale-up
 * "balloon pop" from the bottom corner of the bubble for freshly-arrived messages, both sent
 * and received.
 */
class ConversationItemAnimator(
  private val isInMultiSelectMode: () -> Boolean,
  private val shouldPlayMessageAnimations: () -> Boolean,
  private val isParentFilled: () -> Boolean,
  private val shouldUseSlideAnimation: (RecyclerView.ViewHolder) -> Boolean,
  private val isOutgoingMessage: (RecyclerView.ViewHolder) -> Boolean = { false }
) : RecyclerView.ItemAnimator() {

  private enum class Axis { Y, SCALE }

  private data class TweeningInfo(
    val axis: Axis,
    val startValue: Float,
    val endValue: Float,
    val interpolator: TimeInterpolator? = null
  ) {
    fun lerp(progress: Float): Float {
      return startValue + progress * (endValue - startValue)
    }
  }

  private data class AnimationInfo(
    val sharedAnimator: ValueAnimator,
    val tweeningInfo: TweeningInfo
  )

  private val pendingSlideAnimations: MutableMap<RecyclerView.ViewHolder, TweeningInfo> = mutableMapOf()
  private val slideAnimations: MutableMap<RecyclerView.ViewHolder, AnimationInfo> = mutableMapOf()

  override fun animateDisappearance(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo?): Boolean {
    if (viewHolder is ConversationAdapter.HeaderViewHolder &&
      !pendingSlideAnimations.containsKey(viewHolder) &&
      !slideAnimations.containsKey(viewHolder) &&
      shouldPlayMessageAnimations() &&
      isParentFilled()
    ) {
      pendingSlideAnimations[viewHolder] = TweeningInfo(Axis.Y, 0f, viewHolder.itemView.height.toFloat())
      dispatchAnimationStarted(viewHolder)
      return true
    }

    if (viewHolder !is ConversationAdapter.HeaderViewHolder &&
      !pendingSlideAnimations.containsKey(viewHolder) &&
      !slideAnimations.containsKey(viewHolder) &&
      shouldPlayMessageAnimations()
    ) {
      return animateMessagePopOut(viewHolder)
    }

    dispatchAnimationFinished(viewHolder)
    return false
  }

  /**
   * Shrinks a removed (deleted) message down to a small scale around its center while fading it
   * out, the reverse of the arrival "balloon pop".
   */
  private fun animateMessagePopOut(viewHolder: RecyclerView.ViewHolder): Boolean {
    val itemView = viewHolder.itemView
    itemView.pivotX = itemView.width / 2f
    itemView.pivotY = itemView.height / 2f

    pendingSlideAnimations[viewHolder] = TweeningInfo(Axis.SCALE, 1f, POP_START_SCALE, AccelerateInterpolator())
    dispatchAnimationStarted(viewHolder)

    Log.d(TAG, "Dispatched balloon pop-out animation for removed message at ${viewHolder.absoluteAdapterPosition}")
    return true
  }

  override fun animateAppearance(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo?, postLayoutInfo: ItemHolderInfo): Boolean {
    if (viewHolder.absoluteAdapterPosition > 1 || !shouldUseSlideAnimation(viewHolder)) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    if (preLayoutInfo == null) {
      return animateMessagePop(viewHolder)
    }

    return animateSlide(viewHolder, preLayoutInfo, postLayoutInfo)
  }

  /**
   * Pops a freshly-arrived message in from a small scale anchored at the bottom corner of the
   * bubble up to its full size, like a balloon inflating, with a springy overshoot. Outgoing
   * messages pop from the bottom-right (they're right-aligned); incoming messages pop from the
   * bottom-left.
   */
  private fun animateMessagePop(viewHolder: RecyclerView.ViewHolder): Boolean {
    if (isInMultiSelectMode() || !shouldPlayMessageAnimations()) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    if (slideAnimations.containsKey(viewHolder)) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    val itemView = viewHolder.itemView
    itemView.pivotX = if (isOutgoingMessage(viewHolder)) itemView.width.toFloat() else 0f
    itemView.pivotY = itemView.height.toFloat()
    itemView.scaleX = POP_START_SCALE
    itemView.scaleY = POP_START_SCALE
    itemView.alpha = POP_START_SCALE

    pendingSlideAnimations[viewHolder] = TweeningInfo(Axis.SCALE, POP_START_SCALE, 1f, OvershootInterpolator(1.4f))
    dispatchAnimationStarted(viewHolder)

    Log.d(TAG, "Dispatched balloon pop animation for message at ${viewHolder.absoluteAdapterPosition}")
    return true
  }

  private fun animateSlide(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo?, postLayoutInfo: ItemHolderInfo): Boolean {
    if (isInMultiSelectMode() || !shouldPlayMessageAnimations()) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    if (slideAnimations.containsKey(viewHolder)) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    val translationY = if (preLayoutInfo == null) {
      postLayoutInfo.bottom - postLayoutInfo.top
    } else {
      preLayoutInfo.top - postLayoutInfo.top
    }.toFloat()

    if (translationY == 0f) {
      viewHolder.itemView.translationY = 0f
      dispatchAnimationFinished(viewHolder)
      return false
    }

    viewHolder.itemView.translationY = translationY

    pendingSlideAnimations[viewHolder] = TweeningInfo(Axis.Y, translationY, 0f)
    dispatchAnimationStarted(viewHolder)

    Log.d(TAG, "Dispatched slide animation for view at ${viewHolder.absoluteAdapterPosition}")
    return true
  }

  override fun animatePersistence(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo): Boolean {
    return if (!isInMultiSelectMode() && shouldPlayMessageAnimations() && isParentFilled()) {
      if (pendingSlideAnimations.contains(viewHolder) || slideAnimations.containsKey(viewHolder) || !shouldUseSlideAnimation(viewHolder)) {
        dispatchAnimationFinished(viewHolder)
        false
      } else {
        animateSlide(viewHolder, preLayoutInfo, postLayoutInfo)
      }
    } else {
      dispatchAnimationFinished(viewHolder)
      false
    }
  }

  override fun animateChange(oldHolder: RecyclerView.ViewHolder, newHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo): Boolean {
    if (oldHolder != newHolder) {
      dispatchAnimationFinished(oldHolder)
    }

    return animatePersistence(newHolder, preLayoutInfo, postLayoutInfo)
  }

  override fun runPendingAnimations() {
    Log.d(TAG, "Starting ${pendingSlideAnimations.size} animations.")
    runPendingSlideAnimations()
  }

  private fun runPendingSlideAnimations() {
    val animators: MutableList<Animator> = mutableListOf()
    for ((viewHolder, tweeningInfo) in pendingSlideAnimations) {
      val animator = ValueAnimator.ofFloat(0f, 1f)
      slideAnimations[viewHolder] = AnimationInfo(animator, tweeningInfo)
      animator.duration = when {
        tweeningInfo.axis == Axis.SCALE && tweeningInfo.endValue < tweeningInfo.startValue -> POP_OUT_DURATION_MS
        tweeningInfo.axis == Axis.SCALE -> POP_DURATION_MS
        else -> SLIDE_DURATION_MS
      }
      animator.interpolator = tweeningInfo.interpolator ?: LinearInterpolator()
      animator.addUpdateListener {
        if (viewHolder in slideAnimations) {
          val value = tweeningInfo.lerp(it.animatedValue as Float)
          when (tweeningInfo.axis) {
            Axis.SCALE -> {
              viewHolder.itemView.scaleX = value
              viewHolder.itemView.scaleY = value
              viewHolder.itemView.alpha = value.coerceIn(0f, 1f)
            }
            Axis.Y -> viewHolder.itemView.translationY = value
          }
          (viewHolder.itemView.parent as RecyclerView?)?.invalidate()
        }
      }
      animator.doOnEnd {
        if (viewHolder in slideAnimations) {
          handleAnimationEnd(viewHolder)
        }
      }
      animators.add(animator)
    }

    AnimatorSet().apply {
      playTogether(animators)
      start()
    }

    pendingSlideAnimations.clear()
  }

  private fun handleAnimationEnd(viewHolder: RecyclerView.ViewHolder) {
    when (slideAnimations[viewHolder]?.tweeningInfo?.axis) {
      Axis.SCALE -> {
        val itemView = viewHolder.itemView
        itemView.scaleX = 1f
        itemView.scaleY = 1f
        itemView.alpha = 1f
        itemView.pivotX = itemView.width / 2f
        itemView.pivotY = itemView.height / 2f
      }
      else -> viewHolder.itemView.translationY = 0f
    }
    slideAnimations.remove(viewHolder)
    dispatchAnimationFinished(viewHolder)
    dispatchFinishedWhenDone()
  }

  override fun endAnimation(item: RecyclerView.ViewHolder) {
    endSlideAnimation(item)
  }

  override fun endAnimations() {
    endSlideAnimations()
    dispatchAnimationsFinished()
  }

  override fun isRunning(): Boolean {
    return slideAnimations.values.any { it.sharedAnimator.isRunning }
  }

  override fun onAnimationFinished(viewHolder: RecyclerView.ViewHolder) {
    val parent = (viewHolder.itemView.parent as? RecyclerView)
    parent?.post { parent.invalidate() }
  }

  private fun endSlideAnimation(item: RecyclerView.ViewHolder) {
    slideAnimations[item]?.sharedAnimator?.cancel()
  }

  private fun endSlideAnimations() {
    slideAnimations.values.map { it.sharedAnimator }.forEach {
      it.cancel()
    }
  }

  private fun dispatchFinishedWhenDone() {
    if (!isRunning) {
      Log.d(TAG, "Finished running animations.")
      dispatchAnimationsFinished()
    }
  }

  companion object {
    private val TAG = Log.tag(ConversationItemAnimator::class.java)
    private const val SLIDE_DURATION_MS = 150L
    private const val POP_DURATION_MS = 320L
    private const val POP_OUT_DURATION_MS = 200L
    private const val POP_START_SCALE = 0.2f
  }
}
