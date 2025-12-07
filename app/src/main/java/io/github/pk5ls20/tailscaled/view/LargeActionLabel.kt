package io.github.pk5ls20.tailscaled.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import io.github.pk5ls20.tailscaled.R

class LargeActionLabel @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyleAttr, defStyleRes) {

    private val iconView: ImageView
    private val textView: TextView
    private val subtextView: TextView

    var icon: Drawable?
        get() = iconView.drawable
        set(value) {
            iconView.setImageDrawable(value)
        }

    var text: CharSequence?
        get() = textView.text
        set(value) {
            textView.text = value
        }

    var subtext: CharSequence?
        get() = subtextView.text
        set(value) {
            subtextView.text = value
            subtextView.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.component_large_action_label, this, true)

        iconView = findViewById(R.id.icon_view)
        textView = findViewById(R.id.text_view)
        subtextView = findViewById(R.id.subtext_view)

        // Set clickable and focusable with ripple effect
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        background = ContextCompat.getDrawable(context, outValue.resourceId)
        isFocusable = true
        isClickable = true

        // Apply attributes
        context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.LargeActionLabel,
            defStyleAttr,
            defStyleRes
        ).apply {
            try {
                icon = getDrawable(R.styleable.LargeActionLabel_labelIcon)
                text = getString(R.styleable.LargeActionLabel_labelText)
                subtext = getString(R.styleable.LargeActionLabel_labelSubtext)
            } finally {
                recycle()
            }
        }
    }
}
