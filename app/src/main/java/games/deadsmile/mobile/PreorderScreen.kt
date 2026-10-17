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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
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
            Text("Pré-registro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Veja os jogos com inscrições abertas e receba a confirmação por e-mail.")
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Atualizar inscrições") }
        }
        if (state.preorders.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nenhum pré-registro aberto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Quando um jogo abrir as inscrições, ele aparecerá aqui automaticamente.")
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
                    Text("Inscrições abertas")
                    if (selectedSlug != game.slug) {
                        Button(onClick = {
                            selectedSlug = game.slug
                            firstName = ""
                            lastName = ""
                            age = ""
                            email = state.user?.email.orEmpty()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Fazer pré-registro") }
                    } else if (state.user == null) {
                        Text("Entre na sua conta para continuar com a inscrição.")
                        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Entrar na conta") }
                        OutlinedButton(onClick = { selectedSlug = "" }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                    } else {
                        OutlinedTextField(
                            value = firstName, onValueChange = { firstName = it.take(100) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true
                        )
                        OutlinedTextField(
                            value = lastName, onValueChange = { lastName = it.take(100) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Sobrenome") }, singleLine = true
                        )
                        OutlinedTextField(
                            value = age, onValueChange = { value -> if (value.all(Char::isDigit)) age = value.take(3) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Idade") }, singleLine = true
                        )
                        OutlinedTextField(
                            value = email, onValueChange = { email = it.take(254) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("E-mail para confirmação") }, singleLine = true
                        )
                        Text("O e-mail será usado para confirmar a inscrição. Confira os dados antes de enviar.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { onRegister(game.slug, firstName, lastName, age, email) },
                            enabled = !state.busy && firstName.isNotBlank() && lastName.isNotBlank() &&
                                age.toIntOrNull() in 1..120 && email.contains("@"),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Enviar pré-registro")
                        }
                        OutlinedButton(onClick = { selectedSlug = "" }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                    }
                }
            }
        }
    }
}
