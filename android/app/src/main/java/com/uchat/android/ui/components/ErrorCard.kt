package com.uchat.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.core.AppError

/**
 * The standard error surface (spec #41): explains what failed, why, and gives the user Retry + a
 * one-tap "Copy error" action that puts the full secret-free diagnostics on the clipboard.
 */
@Composable
fun ErrorCard(
    error: AppError,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.error_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(error.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                error.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error.detail?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyError(context, error) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy_error))
                }
                if (onRetry != null) {
                    OutlinedButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_retry))
                    }
                }
                onDismiss?.let {
                    OutlinedButton(onClick = it) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}

private fun copyError(context: Context, error: AppError) {
    val text = error.diagnostics()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("UChat error", text))
    Toast.makeText(context, context.getString(R.string.action_copied), Toast.LENGTH_SHORT).show()
}
