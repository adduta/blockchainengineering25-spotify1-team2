package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.ui.components.releases.NonLazyReleaseList
import nl.tudelft.trustchain.musicdao.ui.navigation.Screen
import nl.tudelft.trustchain.musicdao.core.repositories.model.AccountType
import nl.tudelft.trustchain.musicdao.core.services.AccountService

@ExperimentalFoundationApi
@ExperimentalMaterialApi
@Composable
fun Profile(
    artist: Artist,
    releases: List<Album> = listOf(),
    navController: NavController,
    accountService: AccountService,
    onNavigateToUpgrade: () -> Unit,
    isOwnProfile: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Artist Name and Account Type
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.h5
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = artist.accountType.name,
                    style = MaterialTheme.typography.subtitle1
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Account Status (only show for own profile)
        if (isOwnProfile) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Account Status",
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Download Delay: ${artist.accountType.downloadDelayHours} hours",
                        style = MaterialTheme.typography.body1
                    )
                    Text(
                        text = "Total Donations: ${artist.totalDonations} BTC",
                        style = MaterialTheme.typography.body1
                    )
                    if (artist.accountType == AccountType.BASIC) {
                        val missingDonations = accountService.getMissingDonationsForPro(artist)
                        Text(
                            text = "Required for Pro: $missingDonations BTC more",
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.body1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Account Management Button (only show for own profile)
            Button(
                onClick = onNavigateToUpgrade,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (artist.accountType == AccountType.BASIC) {
                        "Upgrade to Pro"
                    } else {
                        "Manage Account"
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Artist Information
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Artist Information",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Public Key: ${artist.publicKey}",
                    style = MaterialTheme.typography.body2
                )
                Text(
                    text = "Bitcoin Address: ${artist.bitcoinAddress}",
                    style = MaterialTheme.typography.body2
                )
                if (artist.biography.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Biography",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = artist.biography,
                        style = MaterialTheme.typography.body1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Releases Section
        if (artist.releaseIds.isNotEmpty()) {
            Text(
                text = "Releases",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            artist.releaseIds.forEach { release ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = release,
                            style = MaterialTheme.typography.subtitle1
                        )
                        Text(
                            text = "Released: $release",
                            style = MaterialTheme.typography.body2
                        )
                    }
                }
            }
        }
    }
}
