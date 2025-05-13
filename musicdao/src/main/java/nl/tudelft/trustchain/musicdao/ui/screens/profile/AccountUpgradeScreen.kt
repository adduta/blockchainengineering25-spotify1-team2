package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import nl.tudelft.trustchain.musicdao.core.repositories.model.AccountType
import nl.tudelft.trustchain.musicdao.ui.SnackbarHandler

@Composable
fun AccountUpgradeScreen(
    navController: NavController,
    viewModel: MyProfileScreenViewModel = hiltViewModel()
) {
    val profile = viewModel.profile.collectAsState()
    val accountService = viewModel.accountService

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Account Status",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        profile.value?.let { artist ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary
                        )
                        Text(
                            text = artist.accountType.displayName,
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
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

            Text(
                text = "Account Tiers",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Basic Tier Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (artist.accountType == AccountType.BASIC)
                                MaterialTheme.colors.primary
                            else
                                MaterialTheme.colors.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = "Basic",
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(start = 8.dp)
                        )
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

            // Pro Tier Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (artist.accountType == AccountType.PRO)
                                MaterialTheme.colors.primary
                            else
                                MaterialTheme.colors.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = "Pro",
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(start = 8.dp)
                        )
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
                        text = "• Requires ${AccountType.PRO.requiredDonations} BTC in total donations",
                        style = MaterialTheme.typography.body1
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (artist.accountType == AccountType.BASIC) {
                val missingDonations = accountService.getMissingDonationsForPro(artist)
                if (missingDonations <= 0) {
                    Button(
                        onClick = {
                            // TODO: Implement payment flow
                            SnackbarHandler.displaySnackbar("Pro upgrade coming soon!")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upgrade to Pro")
                    }
                } else {
                    Text(
                        text = "Donate ${missingDonations} BTC more to unlock Pro features",
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.Donate.createRoute(publicKey = artist.publicKey)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Make a Donation")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        // TODO: Implement downgrade confirmation
                        SnackbarHandler.displaySnackbar("Downgrade to Basic coming soon!")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Downgrade to Basic")
                }
            }
        }
    }
} 