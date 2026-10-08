package com.example.gather

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.tasks.Task
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.FirebaseApp
import java.text.DateFormat
import java.util.Date

object GatherUi {
    val background = Color.rgb(244, 248, 247)
    val ink = Color.rgb(19, 43, 45)
    val muted = Color.rgb(83, 108, 107)
    val green = Color.rgb(23, 108, 91)
    val soft = Color.rgb(230, 243, 239)
    val accent = Color.rgb(114, 226, 188)
    val border = Color.rgb(215, 230, 225)
    private val fonts = mutableMapOf<Int, Typeface>()
    private fun font(context: Context, res: Int): Typeface =
        fonts.getOrPut(res) { ResourcesCompat.getFont(context, res) ?: Typeface.DEFAULT }
    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value).toInt()
    fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun page(parent: ViewGroup): LinearLayout {
        parent.removeAllViews()
        val content = column(parent.context).apply {
            setPadding(dp(context, 22), dp(context, 20), dp(context, 22), dp(context, 36))
        }
        parent.addView(ScrollView(parent.context).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        },
            ViewGroup.LayoutParams(-1, -1))
        return content
    }
    fun text(parent: LinearLayout, value: String, size: Float = 15f, color: Int = muted): TextView =
        TextView(parent.context).apply {
            text = value; textSize = size; setTextColor(color)
            typeface = font(context, R.font.space_grotesk_regular)
            setLineSpacing(dp(context, 2).toFloat(), 1f)
            setPadding(0, dp(context, 4), 0, dp(context, 4))
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    fun title(parent: LinearLayout, value: String) = text(parent, value, 30f, ink).apply {
        typeface = font(context, R.font.space_grotesk_bold)
        letterSpacing = -0.02f
    }
    fun heading(parent: LinearLayout, value: String) = text(parent, value, 22f, ink).apply {
        typeface = font(context, R.font.space_grotesk_medium)
        letterSpacing = -0.01f
        setPadding(0, dp(context, 8), 0, dp(context, 4))
    }
    fun eyebrow(parent: LinearLayout, value: String) = text(parent, value.uppercase(), 11f, green).apply {
        typeface = font(context, R.font.space_grotesk_bold)
        letterSpacing = 0.15f
    }
    fun section(parent: LinearLayout, label: String, title: String) {
        eyebrow(parent, label).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(context, 24)
        }
        heading(parent, title)
    }
    fun card(parent: LinearLayout): LinearLayout = column(parent.context).apply {
        val pad = dp(context, 18); setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(context, 22).toFloat()
            setStroke(dp(context, 1), border)
        }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(context, 14)
        })
    }
    fun hero(parent: LinearLayout, overline: String, headline: String, detail: String): LinearLayout =
        column(parent.context).apply {
            val p = dp(context, 24)
            setPadding(p, p, p, p)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(27, 75, 69), Color.rgb(13, 39, 44))).apply {
                cornerRadius = dp(context, 26).toFloat()
            }
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(context, 16)
            })
            eyebrow(this, overline).setTextColor(accent)
            heading(this, headline).apply {
                textSize = 28f
                setTextColor(Color.WHITE)
                typeface = font(context, R.font.space_grotesk_bold)
            }
            text(this, detail, 14f, Color.rgb(211, 235, 229))
        }
    fun chip(parent: LinearLayout, value: String): TextView = text(parent, value, 12f, green).apply {
        typeface = font(context, R.font.space_grotesk_medium)
        background = GradientDrawable().apply {
            setColor(soft); cornerRadius = dp(context, 14).toFloat()
        }
        setPadding(dp(context, 12), dp(context, 7), dp(context, 12), dp(context, 7))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(context, 10) }
    }
    fun button(parent: LinearLayout, label: String, primary: Boolean = false,
               action: () -> Unit): MaterialButton = MaterialButton(parent.context).apply {
        text = label; isAllCaps = false; minHeight = dp(context, 50)
        typeface = font(context, R.font.space_grotesk_medium)
        textSize = 15f
        letterSpacing = 0.01f
        cornerRadius = dp(context, 15)
        backgroundTintList = ColorStateList.valueOf(if (primary) green else soft)
        setTextColor(if (primary) Color.WHITE else green)
        elevation = 0f
        if (!primary) {
            strokeColor = ColorStateList.valueOf(border)
            strokeWidth = dp(context, 1)
        }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 8) })
        setOnClickListener { action() }
    }
    fun field(parent: LinearLayout, label: String, value: String = "", multiline: Boolean = false): EditText {
        text(parent, label, 13f, ink).apply {
            typeface = font(context, R.font.space_grotesk_medium)
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(context, 14)
        }
        return EditText(parent.context).apply {
            setText(value); textSize = 16f; setTextColor(ink)
            setHintTextColor(muted)
            typeface = font(context, R.font.space_grotesk_regular)
            background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.sp_input)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            setSingleLine(!multiline)
            inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            if (multiline) minLines = 3
            minHeight = dp(context, 54)
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
    fun form(context: Context) = column(context).apply {
        val p = dp(context, 20); setPadding(p, p, p, p)
    }
    fun date(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(time))
}

open class GatherActivity : AppCompatActivity() {
    private var working = false
    fun message(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    fun install(root: View) {
        enableEdgeToEdge()
        root.setBackgroundColor(GatherUi.background)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }
    fun perform(work: () -> Task<*>, done: () -> Unit = {}) {
        if (working) return
        working = true
        try {
            work().addOnCompleteListener { task ->
                working = false
                if (isDestroyed || isFinishing) return@addOnCompleteListener
                if (task.isSuccessful) done()
                else message(task.exception?.localizedMessage ?: "The operation failed.")
            }
        } catch (error: Exception) {
            working = false; message(error.localizedMessage ?: "The operation failed.")
        }
    }
    fun confirm(title: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(title).setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ -> action() }.show()
    }
    fun input(title: String, hint: String, action: (String) -> Unit) {
        val form = GatherUi.form(this)
        val field = GatherUi.field(form, hint)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(title).setView(form)
            .setNegativeButton("Cancel", null).setPositiveButton("Continue", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = field.text.toString().trim()
                if (value.isBlank()) field.error = "Required" else { dialog.dismiss(); action(value) }
            }
        }
        dialog.show()
    }
}

open class SignedInActivity : GatherActivity() {
    protected lateinit var uid: String
    protected lateinit var repository: SocialRepository
    protected var ready = false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (FirebaseApp.initializeApp(this) == null || CloudStore.auth.currentUser == null) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        uid = CloudStore.auth.currentUser!!.uid
        repository = SocialRepository(uid)
        ready = true
    }
    override fun onStart() {
        super.onStart()
        if (ready && CloudStore.auth.currentUser?.uid != uid) {
            ready = false; finish()
        }
    }
}