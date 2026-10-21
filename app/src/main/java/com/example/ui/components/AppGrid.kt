package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AngelCardDark
import com.example.ui.theme.AngelNeonGreen

data class ShortcutAppItem(
    val id: String,
    val name: String,
    val burmeseName: String,
    val icon: ImageVector,
    val packageNameOrUrl: String,
    val backgroundColor: Color
)

val DEFAULT_SHORTCUT_APPS = listOf(
    ShortcutAppItem("facebook", "Facebook", "ဖေ့စ်ဘုတ်", Icons.Filled.Share, "com.facebook.katana", Color(0xFF1877F2)),
    ShortcutAppItem("youtube", "YouTube", "ယူကျု", Icons.Filled.PlayCircle, "com.google.android.youtube", Color(0xFFFF0000)),
    ShortcutAppItem("viber", "Viber", "ဗိုက်ဘာ", Icons.AutoMirrored.Filled.Chat, "com.viber.voip", Color(0xFF7360F2)),
    ShortcutAppItem("telegram", "Telegram", "တယ်လီဂရမ်", Icons.AutoMirrored.Filled.Send, "org.telegram.messenger", Color(0xFF229ED9)),
    ShortcutAppItem("kpay", "KBZPay", "ကေပေး", Icons.Filled.CreditCard, "com.kbzbank.kpay", Color(0xFF003876)),
    ShortcutAppItem("wave", "Wave Money", "ဝေ့မုန်း", Icons.Filled.AccountBalanceWallet, "mymoneypay.w提提.wavepay", Color(0xFFFFB800)),
    ShortcutAppItem("maps", "Maps", "မြေပုံ", Icons.Filled.Place, "com.google.android.apps.maps", Color(0xFF34A853)),
    ShortcutAppItem("google", "Google", "ဂူဂဲလ်", Icons.Filled.Search, "com.google.android.googlequicksearchbox", Color(0xFFEA4335)),
    ShortcutAppItem("gmail", "Gmail", "ဂျီမေးလ်", Icons.Filled.Email, "com.google.android.gm", Color(0xFFEA4335)),
    ShortcutAppItem("phone", "Phone", "ဖုန်း", Icons.Filled.Phone, "tel:", Color(0xFF4CAF50)),
    ShortcutAppItem("settings", "Settings", "ဆက်တင်", Icons.Filled.Settings, "app-settings:", Color(0xFF607D8B)),
    ShortcutAppItem("wifi", "WiFi", "ဝိုင်ဖိုင်", Icons.Filled.Wifi, "app-settings:wifi", Color(0xFF2196F3))
)

@Composable
fun AppGrid(
    apps: List<ShortcutAppItem> = DEFAULT_SHORTCUT_APPS,
    onAppClick: (ShortcutAppItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("app_grid_container"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Shortcuts အက်ပ်များ",
                color = AngelNeonGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 260.dp)
            ) {
                items(apps) { app ->
                    AppGridItem(
                        app = app,
                        onClick = {
                            launchAppOrUrl(context, app)
                            onAppClick(app)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppGridItem(
    app: ShortcutAppItem,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .testTag("app_grid_item_${app.id}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(app.backgroundColor)
        ) {
            Icon(
                imageVector = app.icon,
                contentDescription = app.name,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = app.burmeseName,
            color = Color.LightGray,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun launchAppOrUrl(context: Context, app: ShortcutAppItem) {
    try {
        when {
            app.packageNameOrUrl.startsWith("tel:") -> {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                context.startActivity(intent)
            }
            app.packageNameOrUrl.startsWith("app-settings:wifi") -> {
                context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
            }
            app.packageNameOrUrl.startsWith("app-settings:") -> {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
            app.packageNameOrUrl.startsWith("http://") || app.packageNameOrUrl.startsWith("https://") -> {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.packageNameOrUrl)))
            }
            else -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageNameOrUrl)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                } else {
                    Toast.makeText(context, "${app.name} ကို ထည့်သွင်းမထားပါ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "ဖွင့်၍ မရနိုင်ပါ: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
