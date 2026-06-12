package com.livingroomhq.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import kotlinx.coroutines.launch

/**
 * Glass banner asking the user to make LivingRoom HQ the default home app.
 * Disappears once the role is held or the user dismisses it (persisted).
 */
@Composable
fun DefaultHomeBanner(prefs: LauncherPrefsStore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissed by prefs.defaultPromptDismissed.collectAsState(initial = true)
    var isDefault by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { isDefault = DefaultHomeHelper.isDefaultHome(context) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        isDefault = DefaultHomeHelper.isDefaultHome(context)
    }

    if (!shouldPromptForDefault(isDefault, dismissed)) return

    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column {
            Text("MAKE THIS YOUR HOME", style = HqType.Label)
            Spacer(Modifier.height(6.dp))
            Text("Set LivingRoom HQ as the default launcher", style = HqType.Body)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusableGlassCard(
                    onClick = {
                        DefaultHomeHelper.createRequestIntent(context)?.let(launcher::launch)
                    },
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { _ ->
                    Text("SET AS DEFAULT", style = HqType.Label.copy(color = HqColors.Accent))
                }
                FocusableGlassCard(
                    onClick = { scope.launch { prefs.setDefaultPromptDismissed(true) } },
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { _ ->
                    Text("NOT NOW", style = HqType.Label)
                }
            }
        }
    }
}
