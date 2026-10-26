package top.rootu.ssh.tunnel.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import top.rootu.ssh.tunnel.ui.theme.CyanPrimary
import top.rootu.ssh.tunnel.ui.util.rememberIsTv

/**
 * OutlinedTextField that behaves well with a TV remote.
 *
 * Compose starts an input session (and shows the keyboard) whenever a text field gains focus.
 * On TV that means the keyboard pops up while merely moving through a form with the arrows, and
 * after closing it with Back it comes back because the field is still focused. So on TV the field
 * stays read-only while navigating: OK starts editing, the IME action (Next/Done), closing the
 * keyboard or moving focus away ends it. On phones it is a plain OutlinedTextField.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    val isTv = rememberIsTv()
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var keyboardSeen by remember { mutableStateOf(false) }

    // Back closes the keyboard without reaching the app: leave edit mode when the IME goes away
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(editing, imeVisible) {
        when {
            !editing -> keyboardSeen = false
            imeVisible -> keyboardSeen = true
            keyboardSeen -> {
                keyboardSeen = false
                editing = false
            }
        }
    }

    // Finish editing first, then run the caller's action (or move focus like the default)
    fun KeyboardActionScope.finish(action: (KeyboardActionScope.() -> Unit)?, imeAction: ImeAction) {
        editing = false
        if (action != null) action() else if (imeAction == ImeAction.Next) {
            focusManager.moveFocus(FocusDirection.Next)
        } else {
            defaultKeyboardAction(imeAction)
        }
    }
    val actions = if (!isTv) keyboardActions else KeyboardActions(
        onDone = { finish(keyboardActions.onDone, ImeAction.Done) },
        onNext = { finish(keyboardActions.onNext, ImeAction.Next) },
        onGo = { finish(keyboardActions.onGo, ImeAction.Go) },
        onSearch = { finish(keyboardActions.onSearch, ImeAction.Search) },
        onSend = { finish(keyboardActions.onSend, ImeAction.Send) },
        onPrevious = { finish(keyboardActions.onPrevious, ImeAction.Previous) }
    )

    val hint = when {
        supportingText != null -> supportingText
        isTv && focused && !editing -> "Press OK to edit"
        else -> null
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = isTv && !editing,
        label = if (label != null) {
            { Text(label) }
        } else {
            null
        },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = singleLine,
        maxLines = maxLines,
        isError = isError,
        supportingText = if (hint != null) {
            { Text(hint, color = if (isError) Color.Unspecified else CyanPrimary) }
        } else {
            null
        },
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        leadingIcon = leadingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = actions,
        shape = shape,
        colors = colors,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) editing = false
            }
            .onPreviewKeyEvent { event ->
                val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (!isTv || editing || !isOk) return@onPreviewKeyEvent false
                // Swallow both key down and up; start editing on release
                if (event.type == KeyEventType.KeyUp) editing = true
                true
            }
    )
}
