package nl.tudelft.trustchain.musicdao

import android.content.ComponentName
import org.bitcoinj.core.Coin
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.lifecycleScope
import nl.tudelft.trustchain.musicdao.core.ipv8.SetupMusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import nl.tudelft.trustchain.musicdao.core.repositories.MusicGossipingService
import nl.tudelft.trustchain.musicdao.core.repositories.album.BatchPublisher
import nl.tudelft.trustchain.musicdao.core.torrent.TorrentEngine
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import nl.tudelft.trustchain.musicdao.core.wallet.DonationWalletManager
import nl.tudelft.trustchain.musicdao.ui.MusicDAOApp
import nl.tudelft.trustchain.musicdao.ui.screens.profile.ProfileScreenViewModel
import nl.tudelft.trustchain.musicdao.ui.screens.release.ReleaseScreenViewModel
import com.frostwire.jlibtorrent.SessionManager
import com.google.common.util.concurrent.Service
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.components.ActivityComponent
import kotlinx.coroutines.*
import nl.tudelft.trustchain.musicdao.core.coin.WalletManager
import javax.inject.Inject
import nl.tudelft.ipv8.attestation.trustchain.ANY_COUNTERPARTY_PK
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlock
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import org.bouncycastle.util.encoders.Hex

/**
 * This maintains the interactions between the UI and seeding/trust-chain
 */
@AndroidEntryPoint
class MusicActivity : AppCompatActivity() {
    @Inject
    lateinit var albumRepository: AlbumRepository

    @Inject
    lateinit var artistRepository: ArtistRepository

    @OptIn(DelicateCoroutinesApi::class)
    @Inject
    lateinit var torrentEngine: TorrentEngine

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var walletService: WalletService

    @Inject
    lateinit var walletManager: WalletManager

    @Inject
    lateinit var batchPublisher: BatchPublisher

    @Inject
    lateinit var setupMusicCommunity: SetupMusicCommunity

    @Inject
    lateinit var musicCommunity: MusicCommunity

    @Inject
    lateinit var donationWalletManager: DonationWalletManager

    lateinit var mService: MusicGossipingService
    var mBound: Boolean = false

    // Add a flag to control leadership
    private var isManualLeader: Boolean = true

    private var walletAddressJob: Job? = null
    private var walletBalanceJob: Job? = null
    private var listenForImProMessages: Job? = null

    @DelicateCoroutinesApi
    @ExperimentalAnimationApi
    @ExperimentalFoundationApi
    @ExperimentalMaterialApi
    @ExperimentalComposeUiApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.provide(this)
        @Suppress("DEPRECATION")
        lifecycleScope.launchWhenStarted {
            setupMusicCommunity.registerListeners()
            albumRepository.refreshCache()
            torrentEngine.seedStrategy()

            startListeningForImProMessages()
            startListeningForMagnetLink()

            if (isManualLeader) {
                Log.d("DonationWallet", "User is the designated leader.")
                donationWalletManager.start() // Only the leader starts the wallet
                startSharingWalletAddress() // Start sharing the address continuously
                startSharingWalletBalance() // Start sharing the balance continuously
                donationWalletManager.startLottery()
            } else {
                Log.d("DonationWallet", "User is not the designated leader.")
                startFetchingWalletAddress()
                startFetchingWalletBalance()
            }
        }

        iterativelyFetchReleases()
        Intent(this, MusicGossipingService::class.java).also { intent ->
            startService(intent)
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }

        Log.d(
            "MusicDao2",
            "DEBUG: $walletManager"
        )

        Log.d(
            "MusicDao2",
            "${walletManager.kit.state()}"
        )

        if (walletManager.kit.state() == Service.State.RUNNING) {
            val scope = CoroutineScope(Dispatchers.IO)
            val address = walletService.protocolAddress().toString()
            Log.d("MusicDao2", "onSetupCompletedListener (1)")

            scope.launch {
                val me = artistRepository.getMyself()
                Log.d("MusicDao2", "onSetupCompletedListener (2)")
                if (me != null) {
                    if (me.bitcoinAddress != address) {
                        Log.d("MusicDao2", "onSetupCompletedListener (3)")
                        artistRepository.edit(me.name, address, me.socials, me.biography)
                    }
                } else {
                    Log.d("MusicDao2", "onSetupCompletedListener (4)")
                    artistRepository.edit("Artist ${(0..10_000).random()}", address, "Socials", "Biography")
                }
            }
        }

        walletManager.addOnSetupCompletedListener {
            val scope = CoroutineScope(Dispatchers.IO)
            val address = walletService.protocolAddress().toString()
            Log.d("MusicDao2", "onSetupCompletedListener (1)")

            scope.launch {
                val me = artistRepository.getMyself()
                Log.d("MusicDao2", "onSetupCompletedListener (2)")
                if (me != null) {
                    if (me.bitcoinAddress != address) {
                        Log.d("MusicDao2", "onSetupCompletedListener (3)")
                        artistRepository.edit(me.name, address, me.socials, me.biography)
                    }
                } else {
                    Log.d("MusicDao2", "onSetupCompletedListener (4)")
                    artistRepository.edit(
                        "Artist ${(0..10_000).random()}",
                        address,
                        "Socials",
                        "Biography"
                    )
                }
            }
        }

        walletService.addOnSetupCompletedListener {
            val scope = CoroutineScope(Dispatchers.IO)
            val address = walletService.protocolAddress().toString()
            Log.d("MusicDao2", "onSetupCompletedListener (1)")

            scope.launch {
                val me = artistRepository.getMyself()
                Log.d("MusicDao2", "onSetupCompletedListener (2)")
                if (me != null) {
                    if (me.bitcoinAddress != address) {
                        Log.d("MusicDao2", "onSetupCompletedListener (3)")
                        artistRepository.edit(me.name, address, me.socials, me.biography)
                    }
                } else {
                    Log.d("MusicDao2", "onSetupCompletedListener (4)")
                    artistRepository.edit("Artist ${(0..10_000).random()}", address, "Socials", "Biography")
                }
            }
        }

        setContent {
            MusicDAOApp()
        }
    }

    /**
     * On discovering a half block, with tag publish_release, agree it immediately (for now). In the
     * future there will be logic added here to determine whether an upload was done by the correct
     * artist/label (artist passport).
     */
    override fun onDestroy() {
        super.onDestroy()
        if (mBound) {
            unbindService(mConnection)
        }
        // Stop the donation wallet manager
        donationWalletManager.stop()
        // Cancel the job when the activity is destroyed
        walletAddressJob?.cancel()
        walletBalanceJob?.cancel()
    }

    private val mConnection =
        object : ServiceConnection {
            // Called when the connection with the service is established
            override fun onServiceConnected(
                className: ComponentName,
                service: IBinder
            ) {
                val binder = service as MusicGossipingService.LocalBinder
                mService = binder.getService()
                mBound = true
            }

            // Called when the connection with the service disconnects unexpectedly
            override fun onServiceDisconnected(className: ComponentName) {
                mBound = false
            }
        }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uris = uriListFromLocalFiles(intent = data!!)
        AppContainer.currentCallback(uris)
    }

    private fun uriListFromLocalFiles(intent: Intent): List<Uri> {
        // This should be reached when the chooseFile intent is completed and the user selected
        // an audio file
        val uriList = mutableListOf<Uri>()
        val singleFileUri = intent.data
        if (singleFileUri != null) {
            // Only one file is selected
            uriList.add(singleFileUri)
        }
        val clipData = intent.clipData
        if (clipData != null) {
            // Multiple files are selected
            val count = clipData.itemCount
            for (i in 0 until count) {
                val uri = clipData.getItemAt(i).uri
                uriList.add(uri)
            }
        }
        return uriList
    }

    private fun iterativelyFetchReleases() {
        @Suppress("DEPRECATION")
        lifecycleScope.launchWhenStarted {
            while (isActive) {
                albumRepository.refreshCache()
                delay(3000)
            }
        }
    }

    override fun startActivityForResult(
        intent: Intent?,
        requestCode: Int
    ) {
        require(!(requestCode != -1 && requestCode and -0x10000 != 0)) { "Can only use lower 16 bits for requestCode" }
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode)
    }

    // Function to fetch the wallet address from a shared location
    private fun fetchWalletAddressFromSharedLocation(): String {
        // Fetch the latest block of type 'DONATION_WALLET_ADDRESS' from the trustchain
        val blocks = musicCommunity.database.getBlocksWithType("DONATION_WALLET_ADDRESS")
        Log.d("DonationWallet", "Retrieved blocks: $blocks")

        val latest =
            blocks.maxByOrNull { it.timestamp } ?: run {
                Log.w("DonationWallet", "No blocks found for type 'DONATION_WALLET_ADDRESS'")
                return ""
            }

        val address = latest.transaction["address"] as? String
        Log.d("DonationWallet", "Latest block address: $address")

        return address ?: run {
            Log.w("DonationWallet", "Address is null for the latest block")
            ""
        }
    }

    private fun startFetchingWalletAddress() {
        walletAddressJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val walletAddress = fetchWalletAddressFromSharedLocation()
                    Log.d("DonationWallet", "Fetched wallet address from shared location: $walletAddress")
                    donationWalletManager.globalDonationAddress = walletAddress

                    // Delay for a specified interval before fetching again
                    delay(1000) // Fetch every 5 seconds (adjust as needed)
                }
            }
    }

    private fun startSharingWalletAddress() {
        walletAddressJob =
            CoroutineScope(Dispatchers.IO).launch {
                val walletAddress = donationWalletManager.getDonationAddress()
                val tx =
                    mapOf(
                        "address" to walletAddress
                    )
                // Log the transaction map
                Log.d("DonationWallet", "Transaction map: $tx")

                // Create a proposal block with ANY_COUNTERPARTY_PK to broadcast to all peers
                val result =
                    musicCommunity.createProposalBlock(
                        "DONATION_WALLET_ADDRESS",
                        tx,
                        // Use ANY_COUNTERPARTY_PK instead of specific peer
                        ANY_COUNTERPARTY_PK
                    )
                while (isActive) {
                    musicCommunity.sendBlock(result, ttl = 2)
                    Log.d("DonationWallet", "Wallet address shared: $walletAddress")
                    delay(5000) // Adjust the delay as needed (e.g., every 5 seconds)
                }
            }
    }

    // Function to fetch the wallet address from a shared location
    private fun fetchWalletBalanceFromSharedLocation(): String {
        // Fetch the latest block of type 'DONATION_WALLET_BALANCE' from the trustchain
        val blocks = musicCommunity.database.getBlocksWithType("DONATION_WALLET_BALANCE")
        Log.d("DonationWallet", "Retrieved blocks: $blocks")

        val latest =
            blocks.maxByOrNull { it.timestamp } ?: run {
                Log.w("DonationWallet", "No blocks found for type 'DONATION_WALLET_BALANCE'")
                return Coin.ZERO.toString()
            }

        val balance = latest.transaction["balance"] as? String
        Log.d("DonationWallet", "Latest block balance: $balance")

        return balance ?: run {
            Log.w("DonationWallet", "Balance is null for the latest block")
            Coin.ZERO.toString()
        }
    }

    private fun startFetchingWalletBalance() {
        walletBalanceJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val walletBalance = fetchWalletBalanceFromSharedLocation()
                    Log.d("DonationWallet", "Fetched wallet balance from shared location: $walletBalance")
                    donationWalletManager.globalDonationBalance = Coin.valueOf(walletBalance.toLong())

                    // Delay for a specified interval before fetching again
                    delay(1000) // Fetch every 5 seconds (adjust as needed)
                }
            }
    }

    private fun startSharingWalletBalance() {
        walletBalanceJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val walletBalance = donationWalletManager.getBalance().toString()
                    val tx =
                        mapOf(
                            "balance" to walletBalance
                        )
                    // Log the transaction map
                    Log.d("DonationWallet", "Transaction map: $tx")

                    // Create a proposal block with ANY_COUNTERPARTY_PK to broadcast to all peers
                    val result =
                        musicCommunity.createProposalBlock(
                            "DONATION_WALLET_BALANCE",
                            tx,
                            // Use ANY_COUNTERPARTY_PK instead of specific peer
                            ANY_COUNTERPARTY_PK
                        )

                    musicCommunity.sendBlock(result, ttl = 2)
                    Log.d("DonationWallet", "Wallet balance shared: $walletBalance")
                    delay(5000) // Adjust the delay as needed (e.g., every 5 seconds)
                }
            }
    }

    private val seenBlockIds = mutableSetOf<Int>()

    private fun startListeningForImProMessages() {
        listenForImProMessages = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val blocks = musicCommunity.database.getBlocksWithType(UserTierBlock.BLOCK_TYPE)

                val myPublicKey = musicCommunity.publicKeyHex()
                val myAlbums = albumRepository.getAlbumsFromArtist(myPublicKey)

                for (block in blocks) {
                    if (block.hashNumber in seenBlockIds) {
                        continue
                    }
                    val userThatPromotedToProString = block.transaction["userId"] as? String
                    if (userThatPromotedToProString != null) {
                        try {
                            val userThatPromotedToPro: ByteArray =
                                Hex.decode(userThatPromotedToProString)
                            for (album in myAlbums) {
                                // Publish magnet for the user that was promoted to Pro account.
                                val tx =
                                    mapOf(
                                        "album_id" to album.id,
                                        "magnet" to album.magnet
                                    )
                                // Create a proposal block with ANY_COUNTERPARTY_PK to broadcast to all peers
                                val result =
                                    musicCommunity.createProposalBlock(
                                        "MAGNET_LINK",
                                        tx,
                                        userThatPromotedToPro
                                    )
                                musicCommunity.sendBlock(result, ttl = 2)
                            }
                        } catch (e: Exception) {
                            println("Failed to decode userId: $userThatPromotedToProString")
                        }
                    }
                }
                delay(5000) // Optional polling interval
            }
        }
    }

    @EntryPoint
    @InstallIn(ActivityComponent::class)
    interface ViewModelFactoryProvider {
        fun noteDetailViewModelFactory(): ReleaseScreenViewModel.ReleaseScreenViewModelFactory

        fun profileScreenViewModelFactory(): ProfileScreenViewModel.ProfileScreenViewModelFactory
    }
}
