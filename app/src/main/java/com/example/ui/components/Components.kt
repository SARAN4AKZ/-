package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DocumentStatus
import com.example.data.model.DocumentType
import com.example.domain.InnValidationResult
import com.example.domain.InnValidator
import com.example.ui.theme.LocalLedgerColors
import com.example.ui.theme.MonoNumberStyle

@Composable
fun DocumentTypeBadge(docType: DocumentType) {
    val ledger = LocalLedgerColors.current
    val bg: Color
    val fg: Color
    if (docType == DocumentType.INVOICE) {
        bg = ledger.accentBlueContainer
        fg = ledger.onAccentBlueContainer
    } else {
        bg = MaterialTheme.colorScheme.secondaryContainer
        fg = MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = docType.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DocumentStatusBadge(status: DocumentStatus) {
    val ledger = LocalLedgerColors.current
    val (bg, fg) = if (status == DocumentStatus.ISSUED) {
        ledger.successContainer to ledger.onSuccessContainer
    } else {
        ledger.warningContainer to ledger.onWarningContainer
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun InnSearchRow(
    inn: String,
    onInnChange: (String) -> Unit,
    isLoading: Boolean,
    onLookupClick: () -> Unit,
    label: String = "ИНН (10 или 12 цифр)",
    buttonText: String = "Найти по ИНН",
    testTagPrefix: String = "inn",
    statusMessage: String? = null,
    isError: Boolean = false
) {
    val cleanInn = inn.filter { it.isDigit() }.take(12)
    val validation = if (cleanInn.isNotEmpty()) InnValidator.validate(cleanInn) else null

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = cleanInn,
                onValueChange = { next ->
                    onInnChange(next.filter { it.isDigit() }.take(12))
                },
                label = { Text(label) },
                placeholder = { Text("7707083893") },
                singleLine = true,
                textStyle = MonoNumberStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { onLookupClick() }),
                isError = cleanInn.length >= 10 && validation is InnValidationResult.Invalid,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_input")
            )
            Button(
                onClick = onLookupClick,
                enabled = !isLoading && cleanInn.length >= 10,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .height(50.dp)
                    .testTag("${testTagPrefix}_lookup_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onTertiary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(buttonText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (cleanInn.length >= 10 && validation is InnValidationResult.Invalid) {
            Text(
                text = validation.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (!statusMessage.isNullOrBlank()) {
            Surface(
                color = if (isError) MaterialTheme.colorScheme.errorContainer
                else LocalLedgerColors.current.successContainer,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    else LocalLedgerColors.current.success.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else LocalLedgerColors.current.onSuccessContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}
