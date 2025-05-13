package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.tudelft.trustchain.musicdao.core.repositories.model.AccountType
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.core.services.AccountService

@Composable
fun AccountUpgradeScreen(
    artist: Artist,
    accountService: AccountService,
    onNavigateBack: () -> Unit,
    onUpgradeSuccess: (Artist) -> Unit
) {
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Account Upgrade",
                style = MaterialTheme.typography.h6
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Account Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Current Account: ${artist.accountType.name}",
                    style = MaterialTheme.typography.h6
                )
                Text(
                    text = "Download Delay: ${artist.accountType.downloadDelayHours} hours",
                    style = MaterialTheme.typography.body1
                )
                Text(
                    text = "Total Donations: ${artist.totalDonations} BTC",
                    style = MaterialTheme.typography.body1
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Basic Tier Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Basic Tier",
                        style = MaterialTheme.typography.h6
                    )
                    if (artist.accountType == AccountType.BASIC) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(Current)",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
                Text(
                    text = "• 24-hour delay for new music downloads",
                    style = MaterialTheme.typography.body1
                )
                Text(
                    text = "• Free to use",
                    style = MaterialTheme.typography.body1
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pro Tier Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pro Tier",
                        style = MaterialTheme.typography.h6
                    )
                    if (artist.accountType == AccountType.PRO) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(Current)",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
                Text(
                    text = "• Instant access to new music",
                    style = MaterialTheme.typography.body1
                )
                Text(
                    text = "• No download delays",
                    style = MaterialTheme.typography.body1
                )
                Text(
                    text = "• Required: ${AccountType.PRO.requiredDonations} BTC in donations",
                    style = MaterialTheme.typography.body1
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Upgrade/Downgrade Button
        when (artist.accountType) {
            AccountType.BASIC -> {
                val missingDonations = accountService.getMissingDonationsForPro(artist)
                if (missingDonations > 0) {
                    Text(
                        text = "Need $missingDonations BTC more in donations to upgrade",
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(
                        onClick = { /* TODO: Navigate to donation screen */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Make a Donation")
                    }
                } else {
                    Button(
                        onClick = {
                            accountService.upgradeToPro(artist)
                                .onSuccess { updatedArtist ->
                                    onUpgradeSuccess(updatedArtist)
                                }
                                .onFailure { error ->
                                    showError = true
                                    errorMessage = error.message ?: "Failed to upgrade account"
                                }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upgrade to Pro")
                    }
                }
            }
            AccountType.PRO -> {
                OutlinedButton(
                    onClick = {
                        val updatedArtist = accountService.downgradeToBasic(artist)
                        onUpgradeSuccess(updatedArtist)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Downgrade to Basic")
                }
            }
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text("OK")
                }
            }
        )
    }
}
