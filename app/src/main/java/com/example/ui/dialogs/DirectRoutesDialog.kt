package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.permissions.DirectRouteItem
import com.example.permissions.DirectRouteManager
import com.example.permissions.RouteCategory
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Interactive Direct Routes & Permissions Explorer Dialog for Kyaw Gyi AI.
 * Displays all system routes, deep link URIs, granted status, and provides 1-tap testing.
 */
@Composable
fun DirectRoutesDialog(
  onNavigateAppScreen: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val allRoutes = remember(context) { DirectRouteManager.getAllRoutes(context) }

  var selectedCategory by remember { mutableStateOf<RouteCategory?>(null) }
  var searchQuery by remember { mutableStateOf("") }
  var customUriInput by remember { mutableStateOf("kyawgyi://permissions/microphone") }

  val filteredRoutes = remember(allRoutes, selectedCategory, searchQuery) {
    allRoutes.filter { route ->
      val matchesCategory = selectedCategory == null || route.category == selectedCategory
      val matchesSearch = searchQuery.isBlank() ||
        route.nameMy.contains(searchQuery, ignoreCase = true) ||
        route.nameEn.contains(searchQuery, ignoreCase = true) ||
        route.uri.contains(searchQuery, ignoreCase = true) ||
        (route.aliasUri?.contains(searchQuery, ignoreCase = true) == true)
      matchesCategory && matchesSearch
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(BackgroundDark)
        .border(1.2.dp, SurfaceCardBorder, RoundedCornerShape(24.dp))
        .testTag("direct_routes_dialog"),
      color = BackgroundDark
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
      ) {
        // 1. Top Header Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                  Brush.linearGradient(
                    listOf(Color(0xFF0284C7), EmeraldPrimary)
                  )
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.AltRoute,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
              )
            }

            Column {
              Text(
                text = "တိုက်ရိုက်လမ်းကြောင်းနှင့် ဆက်တင်များ",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "Direct Routes & System Permissions Navigator",
                fontSize = 11.sp,
                color = TextMuted
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(SurfaceCard)
              .testTag("btn_close_direct_routes")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = TextMuted
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Direct URI Quick Launcher Box
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .padding(12.dp)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "URI တိုက်ရိုက် စမ်းသပ်ခေါ်ဆိုမှု (Deep Link Tester)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = EmeraldLight
              )
              Text(
                text = "Scheme: kyawgyi:// & ကောင်ဂျီး://",
                fontSize = 10.sp,
                color = TextMuted
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedTextField(
                value = customUriInput,
                onValueChange = { customUriInput = it },
                placeholder = { Text("kyawgyi://... သို့ ကောင်ဂျီး://...", fontSize = 12.sp, color = TextMuted) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("input_direct_route_uri"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedBorderColor = EmeraldPrimary,
                  unfocusedBorderColor = SurfaceCardBorder,
                  focusedContainerColor = Color(0xFF0B1320),
                  unfocusedContainerColor = Color(0xFF0B1320)
                ),
                shape = RoundedCornerShape(10.dp)
              )

              Button(
                onClick = {
                  DirectRouteManager.executeRoute(customUriInput, context, onNavigateAppScreen)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_launch_custom_uri")
              ) {
                Icon(
                  imageVector = Icons.Default.PlayArrow,
                  contentDescription = "Launch",
                  tint = Color.Black,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("ဖွင့်ပါ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Search & Filter Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("လမ်းကြောင်း ရှာဖွေပါ...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = {
              Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                }
              }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedBorderColor = Color(0xFF38BDF8),
              unfocusedBorderColor = SurfaceCardBorder,
              focusedContainerColor = SurfaceCard,
              unfocusedContainerColor = SurfaceCard
            ),
            shape = RoundedCornerShape(10.dp)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // All Chip
          val isAllSelected = selectedCategory == null
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .background(if (isAllSelected) EmeraldPrimary else SurfaceCard)
              .clickable { selectedCategory = null }
              .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "အားလုံး (${allRoutes.size})",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (isAllSelected) Color.Black else Color.White
            )
          }

          RouteCategory.values().forEach { cat ->
            val isSelected = selectedCategory == cat
            Box(
              modifier = Modifier
                .weight(1.3f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) EmeraldPrimary else SurfaceCard)
                .clickable { selectedCategory = cat }
                .padding(vertical = 7.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = cat.displayNameMy,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.Black else Color.White
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Routes List
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredRoutes, key = { it.id }) { route ->
            DirectRouteCard(
              route = route,
              onSelectUri = { customUriInput = it },
              onLaunch = {
                route.onDirectExecute(context, onNavigateAppScreen)
              },
              onCopy = {
                DirectRouteManager.copyToClipboard(context, route.uri, route.nameEn)
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DirectRouteCard(
  route: DirectRouteItem,
  onSelectUri: (String) -> Unit,
  onLaunch: () -> Unit,
  onCopy: () -> Unit
) {
  val context = LocalContext.current
  val isGranted = remember(route, context) { route.isGrantedCheck(context) }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(SurfaceCard)
      .border(1.dp, SurfaceCardBorder, RoundedCornerShape(14.dp))
      .padding(12.dp)
      .testTag("route_card_${route.id}")
  ) {
    Column {
      // Top Row: Title, Category & Status Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = route.nameMy,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Text(
            text = route.nameEn,
            fontSize = 11.sp,
            color = TextMuted
          )
        }

        // Status Badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
              when {
                route.category == RouteCategory.APP_FEATURE -> Color(0x2238BDF8)
                isGranted -> Color(0x2210B981)
                else -> Color(0x22EF4444)
              }
            )
            .border(
              1.dp,
              when {
                route.category == RouteCategory.APP_FEATURE -> Color(0x6638BDF8)
                isGranted -> Color(0x6610B981)
                else -> Color(0x66EF4444)
              },
              RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = when {
                route.category == RouteCategory.APP_FEATURE -> Icons.Default.Settings
                isGranted -> Icons.Default.CheckCircle
                else -> Icons.Default.ErrorOutline
              },
              contentDescription = null,
              tint = when {
                route.category == RouteCategory.APP_FEATURE -> Color(0xFF38BDF8)
                isGranted -> EmeraldLight
                else -> Color(0xFFEF4444)
              },
              modifier = Modifier.size(12.dp)
            )
            Text(
              text = when {
                route.category == RouteCategory.APP_FEATURE -> "APP ROUTE"
                isGranted -> "GRANTED"
                else -> "ACTION NEEDED"
              },
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = when {
                route.category == RouteCategory.APP_FEATURE -> Color(0xFF38BDF8)
                isGranted -> EmeraldLight
                else -> Color(0xFFFCA5A5)
              }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = route.descriptionMy,
        fontSize = 11.sp,
        color = TextSecondary
      )

      Spacer(modifier = Modifier.height(8.dp))

      // URI & Action Buttons Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF0B1320))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = route.uri,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = EmeraldLight,
            modifier = Modifier.clickable { onSelectUri(route.uri) }
          )
          if (route.aliasUri != null) {
            Text(
              text = route.aliasUri,
              fontSize = 10.sp,
              color = Color(0xFF38BDF8),
              modifier = Modifier.clickable { onSelectUri(route.aliasUri) }
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          // Copy Button
          IconButton(
            onClick = onCopy,
            modifier = Modifier.size(28.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy URI",
              tint = TextMuted,
              modifier = Modifier.size(14.dp)
            )
          }

          // Open Directly Button
          Button(
            onClick = onLaunch,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isGranted) Color(0xFF1E293B) else EmeraldPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(28.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = if (isGranted) "တိုက်ရိုက်စစ်ဆေး" else "ခွင့်ပြုရန် ဖွင့်ပါ",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) Color.White else Color.Black
              )
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (isGranted) Color.White else Color.Black,
                modifier = Modifier.size(12.dp)
              )
            }
          }
        }
      }
    }
  }
}
