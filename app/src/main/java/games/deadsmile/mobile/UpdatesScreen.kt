package games.deadsmile.mobile

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.dp
import games.deadsmile.mobile.data.GitHubRelease
import games.deadsmile.mobile.data.GitHubReleaseRepository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZoneId
import java.util.Locale

@Composable
fun UpdatesScreen(
    pad: PaddingValues,
    releases: List<GitHubRelease>,
    loading: Boolean,
    error: String?,
    installedVersion: String,
    updateBusyVersion: String?,
    updateProgress: Int,
    autoUpdateError: String?,
    allowInstall: Boolean,
    isPlayDistribution: Boolean,
    onRefresh: () -> Unit,
    onInstall: (GitHubRelease) -> Unit,
    onOpenRelease: (String) -> Unit
) {
    val context = LocalContext.current
    val locale = AppI18n.selectedLocale(context)
    Column(
        Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Deadsmile Games", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Versão instalada: $installedVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when {
                        isPlayDistribution -> "As atualizações desta versão são gerenciadas pela Google Play."
                        !allowInstall -> "Atualizações automáticas disponíveis apenas no APK assinado para distribuição direta."
                        loading -> "Verificando as releases do GitHub…"
                        releases.isEmpty() -> "Ainda não há releases publicadas para Deadsmile Games."
                        else -> "Histórico público de versões e arquivos disponibilizados pelo projeto."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (updateBusyVersion != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        Text("Baixando versão $updateBusyVersion · $updateProgress%")
                    }
                    LinearProgressIndicator(
                        progress = { (updateProgress.coerceIn(0, 100) / 100f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (autoUpdateError != null) {
                    Text(autoUpdateError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text("Atualizar histórico")
                }
            }
        }

        when {
            loading && releases.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator()
                Text("Carregando releases do GitHub…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            releases.isEmpty() && error == null -> Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nenhuma release publicada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Quando uma versão oficial for publicada no repositório, ela aparecerá aqui com data, notas e APK.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            releases.isNotEmpty() -> LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(releases, key = { it.id }) { release ->
                    val isNewer = GitHubReleaseRepository.compareVersions(release.version, installedVersion) > 0
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    release.name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                listOf(release.tagName, formatReleaseDate(release.publishedAt, locale), if (release.prerelease) "Pré-lançamento" else "").filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (release.body.isNotBlank()) {
                                Text(release.body, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
                            }
                            if (release.apkName != null) {
                                Text(
                                    listOf(release.apkName, release.apkSize.takeIf { it > 0L }?.let(::formatBytes)).filterNotNull().joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text("Esta release não inclui um APK instalável.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (release.apkUrl != null && isNewer && isPlayDistribution) {
                                    Text("Esta versão será atualizada pela Google Play.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                } else if (release.apkUrl != null && isNewer) {
                                    Button(
                                        onClick = { onInstall(release) },
                                        enabled = allowInstall && updateBusyVersion == null && !release.prerelease && !release.draft,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        androidx.compose.material3.Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(Modifier.padding(horizontal = 3.dp))
                                        Text(if (allowInstall) "Instalar atualização" else "Build de distribuição necessária")
                                    }
                                }
                                if (release.htmlUrl.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { onOpenRelease(release.htmlUrl) },
                                        modifier = if (release.apkUrl != null && isNewer) Modifier else Modifier.fillMaxWidth()
                                    ) {
                                        androidx.compose.material3.Icon(Icons.Default.OpenInNew, contentDescription = null)
                                        Spacer(Modifier.padding(horizontal = 3.dp))
                                        Text("Ver release")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatReleaseDate(value: String, locale: Locale): String {
    val date = runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrNull()
        ?: return value
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.1f GiB", value / (1024.0 * 1024.0 * 1024.0))
    value >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MiB", value / (1024.0 * 1024.0))
    value >= 1024L -> String.format(Locale.ROOT, "%.1f KiB", value / 1024.0)
    else -> "$value B"
}

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    MaterialText(
        text = AppI18n.translate(LocalContext.current, text),
        modifier = modifier, style = style, color = color, fontWeight = fontWeight,
        maxLines = maxLines, overflow = overflow
    )
}

