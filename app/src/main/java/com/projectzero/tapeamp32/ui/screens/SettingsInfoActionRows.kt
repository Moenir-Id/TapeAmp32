package com.projectzero.tapeamp32.ui.screens

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.ui.theme.*

@Composable
internal fun SettingsInfoRow(
    label: String,
    value: String
) {

    SettingsRowCard {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = label,
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 9.sp
            )

            Text(
                text = value,
                color = TextLight,
                fontFamily = MonoFont,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
internal fun SettingsActionRow(
    label: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(
                RoundedCornerShape(4.dp)
            )
            .background(
                Gold.copy(alpha = 0.10f)
            )
            .border(
                width = 0.8.dp,
                color = Gold.copy(alpha = 0.65f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 11.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector = Icons.Filled.OpenInNew,
            contentDescription = null,
            tint = GoldBright,
            modifier = Modifier.size(17.dp)
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Text(
            text = label,
            color = GoldBright,
            fontFamily = MonoFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
