package games.deadsmile.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import games.deadsmile.mobile.data.PreorderOption
import games.deadsmile.mobile.data.UiState

@Composable
internal fun PreorderScreen(
    state: UiState,
    pad: PaddingValues,
    onRefresh: () -> Unit,
    onRegister: (String, String, String, String, String) -> Unit,
    onSignIn: () -> Unit
) {
    val context = LocalContext.current
    val t: (String) -> String = { AppI18n.translate(context, it) }
    var selectedSlug by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(state.user?.email.orEmpty()) }

    LaunchedEffect(state.user?.email) {
        if (email.isBlank()) email = state.user?.email.orEmpty()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(t("Pré-registro"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(t("Veja os jogos com inscrições abertas e receba a confirmação por e-mail."))
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(t("Atualizar inscrições")) }
        }
        if (state.preorderLoading) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
        if (!state.preorderLoadError.isNullOrBlank()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t(state.preorderLoadError), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(t("Tentar novamente")) }
                    }
                }
            }
        }
        if (state.preorders.isEmpty() && !state.preorderLoading && state.preorderLoadError.isNullOrBlank()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("Nenhum pré-registro aberto"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(t("Quando um jogo abrir as inscrições, ele aparecerá aqui automaticamente."))
                    }
                }
            }
        }
        items(state.preorders, key = { it.slug }) { game ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!game.coverImage.isNullOrBlank()) {
                        AsyncImage(
                            model = game.coverImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(170.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(game.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(t("Inscrições abertas"))
                    if (state.preorderErrorSlug == game.slug && !state.preorderError.isNullOrBlank()) {
                        Text(t(state.preorderError), color = MaterialTheme.colorScheme.error)
                    }
                    if (state.preorderMessageSlug == game.slug && !state.preorderMessage.isNullOrBlank()) {
                        Text(t(state.preorderMessage), color = MaterialTheme.colorScheme.primary)
                    }
                    if (selectedSlug != game.slug) {
                        Button(onClick = {
                            selectedSlug = game.slug
                            firstName = ""
                            lastName = ""
                            age = ""
                            email = state.user?.email.orEmpty()
                        }, modifier = Modifier.fillMaxWidth()) { Text(t("Fazer pré-registro")) }
                    } else if (state.user == null) {
                        Text(t("Entre na sua conta para continuar com a inscrição."))
                        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text(t("Entrar na conta")) }
                        OutlinedButton(onClick = { selectedSlug = "" }, modifier = Modifier.fillMaxWidth()) { Text(t("Cancelar")) }
                    } else {
                        OutlinedTextField(
                            value = firstName, onValueChange = { firstName = it.take(100) },
                            modifier = Modifier.fillMaxWidth(), label = { Text(t("Nome")) }, singleLine = true
                        )
                        OutlinedTextField(
                            value = lastName, onValueChange = { lastName = it.take(100) },
                            modifier = Modifier.fillMaxWidth(), label = { Text(t("Sobrenome")) }, singleLine = true
                        )
                        OutlinedTextField(
                            value = age, onValueChange = { value -> if (value.all(Char::isDigit)) age = value.take(3) },
                            modifier = Modifier.fillMaxWidth(), label = { Text(t("Idade")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = email, onValueChange = { email = it.take(254) },
                            modifier = Modifier.fillMaxWidth(), label = { Text(t("E-mail para confirmação")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        Text(t("O e-mail será usado para confirmar a inscrição. Confira os dados antes de enviar."), style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { onRegister(game.slug, firstName, lastName, age, email) },
                            enabled = !state.busy && firstName.isNotBlank() && lastName.isNotBlank() &&
                                age.toIntOrNull()?.let { it in 1..120 } == true &&
                                android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() &&
                                state.preorderMessageSlug != game.slug,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(t("Enviar pré-registro"))
                        }
                        OutlinedButton(onClick = { selectedSlug = "" }, modifier = Modifier.fillMaxWidth()) { Text(t("Cancelar")) }
                    }
                }
            }
        }
    }
}
