package nl.tudelft.trustchain.musicdao.core.repositories

import io.mockk.*
import kotlinx.coroutines.test.runTest
import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
import nl.tudelft.trustchain.musicdao.core.cache.entities.AlbumEntity
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.UserTierVerifier
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish.ReleasePublishBlockRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.time.temporal.ChronoUnit

class AlbumRepositoryTest {
    private lateinit var database: CacheDatabase
    private lateinit var releasePublishBlockRepository: ReleasePublishBlockRepository
    private lateinit var musicCommunity: MusicCommunity
    private lateinit var userTierVerifier: UserTierVerifier
    private lateinit var repository: AlbumRepository
    private val userPublicKey = "test_publickey"

    @BeforeEach
    fun setup() {
        database = mockk()
        releasePublishBlockRepository = mockk()
        musicCommunity = mockk()
        userTierVerifier = mockk()
        repository = AlbumRepository(
            database = database,
            releasePublishBlockRepository = releasePublishBlockRepository,
            musicCommunity = musicCommunity,
            userTierVerifier = userTierVerifier
        )
    }

    @Test
    fun `test getAlbumsFromCache returns all albums for BASIC user when not access restricted`() = runTest {
        val albumEntities = listOf(
            AlbumEntity(
                id = "1",
                title = "Album 1",
                artist = "Artist 1",
                cover = "cover1.jpg",
                releaseDate = Instant.now().toString(),
                magnet = "magnet:link1",
                infoHash = "infohash1",
                isDownloaded = false,
                publisher = "publisher1",
                root = "root1",
                songs = emptyList(),
                torrentPath = "torrent1.torrent",
                isExclusive = false
            ),
            AlbumEntity(
                id = "2",
                title = "Album 2",
                artist = "Artist 2",
                cover = "cover2.jpg",
                releaseDate = Instant.now().toString(),
                magnet = "magnet:link2",
                infoHash = "infohash2",
                isDownloaded = false,
                publisher = "publisher2",
                root = "root2",
                songs = emptyList(),
                torrentPath = "torrent2.torrent",
                isExclusive = false
            )
        )

        coEvery { database.dao.getAll() } returns albumEntities

        val result = repository.getAlbumsFromCache(userPublicKey)

        assertEquals(2, result.size)
        assertEquals("Album 1", result[0].title)
        assertEquals("Album 2", result[1].title)
        coVerify { database.dao.getAll() }
    }

    @Test
    fun `test getAlbumsFromCache filters out access restricted albums for BASIC user`() = runTest {
        val albumEntities = listOf(
            AlbumEntity(
                id = "1",
                title = "Album 1",
                artist = "Artist 1",
                cover = "cover1.jpg",
                releaseDate = Instant.now().toString(),
                magnet = "access_restricted",
                infoHash = "infohash1",
                isDownloaded = false,
                publisher = "publisher1",
                root = "root1",
                songs = emptyList(),
                torrentPath = "torrent1.torrent",
                isExclusive = false
            ),
            AlbumEntity(
                id = "2",
                title = "Album 2",
                artist = "Artist 2",
                cover = "cover2.jpg",
                releaseDate = Instant.now().toString(),
                magnet = "magnet:link2",
                infoHash = "infohash2",
                isDownloaded = false,
                publisher = "publisher2",
                root = "root2",
                songs = emptyList(),
                torrentPath = "torrent2.torrent",
                isExclusive = false
            )
        )

        coEvery { database.dao.getAll() } returns albumEntities
        every { userTierVerifier.isProUser(any()) } returns false

        val result = repository.getAlbumsFromCache(userPublicKey)

        assertEquals(1, result.size)
        assertEquals("Album 2", result[0].title)
        coVerify {
            database.dao.getAll()
            userTierVerifier.isProUser(any())
        }
    }

    @Test
    fun `test getAlbumsFromCache includes access restricted albums for PRO user`() = runTest {
        val albumEntities = listOf(
            AlbumEntity(
                id = "1",
                title = "Album 1",
                artist = "Artist 1",
                cover = "cover1.jpg",
                releaseDate = Instant.now().toString(),
                magnet = "access_restricted",
                infoHash = "infohash1",
                isDownloaded = false,
                publisher = "publisher1",
                root = "root1",
                songs = emptyList(),
                torrentPath = "torrent1.torrent",
                isExclusive = false
            ),
            AlbumEntity(
                id = "2",
                title = "Album 2",
                artist = "Artist 2",
                cover = "cover2.jpg",
                releaseDate = Instant.now().toString(),
                magnet = "magnet:link2",
                infoHash = "infohash2",
                isDownloaded = false,
                publisher = "publisher2",
                root = "root2",
                songs = emptyList(),
                torrentPath = "torrent2.torrent",
                isExclusive = false
            )
        )

        coEvery { database.dao.getAll() } returns albumEntities
        every { userTierVerifier.isProUser(any()) } returns true

        val result = repository.getAlbumsFromCache(userPublicKey)

        assertEquals(2, result.size)
        assertEquals("Album 1", result[0].title)
        assertEquals("Album 2", result[1].title)
        coVerify {
            database.dao.getAll()
            userTierVerifier.isProUser(any())
        }
    }

    @Test
    fun `test getAlbumsFromCache includes access restricted albums for past delay period`() = runTest {
        val albumEntities = listOf(
            AlbumEntity(
                id = "1",
                title = "Album 1",
                artist = "Artist 1",
                cover = "cover1.jpg",
                releaseDate = Instant.now().minus(8, ChronoUnit.DAYS).toString(),
                magnet = "access_restricted",
                infoHash = "infohash1",
                isDownloaded = false,
                publisher = "publisher1",
                root = "root1",
                songs = emptyList(),
                torrentPath = "torrent1.torrent",
                isExclusive = false
            ),
            AlbumEntity(
                id = "2",
                title = "Album 2",
                artist = "Artist 2",
                cover = "cover2.jpg",
                releaseDate = Instant.now().toString(),
                magnet = "magnet:link2",
                infoHash = "infohash2",
                isDownloaded = false,
                publisher = "publisher2",
                root = "root2",
                songs = emptyList(),
                torrentPath = "torrent2.torrent",
                isExclusive = false
            )
        )

        coEvery { database.dao.getAll() } returns albumEntities
        every { userTierVerifier.isProUser(any()) } returns false

        val result = repository.getAlbumsFromCache(userPublicKey)

        assertEquals(2, result.size)
        assertEquals("Album 1", result[0].title)
        assertEquals("Album 2", result[1].title)
        coVerify {
            database.dao.getAll()
            userTierVerifier.isProUser(any())
        }
    }

    @Test
    fun `test getAlbumsFromCache includes exclusive albums for ULTIMATE user`() = runTest {
        val albumEntities = listOf(
            AlbumEntity(
                id = "1",
                title = "Album 1",
                artist = "Artist 1",
                cover = "cover1.jpg",
                releaseDate = Instant.now().toString(),
                magnet = "access_restricted",
                infoHash = "infohash1",
                isDownloaded = false,
                publisher = "publisher1",
                root = "root1",
                songs = emptyList(),
                torrentPath = "torrent1.torrent",
                isExclusive = true
            ),
            AlbumEntity(
                id = "2",
                title = "Album 2",
                artist = "Artist 2",
                cover = "cover2.jpg",
                releaseDate = Instant.now().toString(),
                magnet = "magnet:link2",
                infoHash = "infohash2",
                isDownloaded = false,
                publisher = "publisher2",
                root = "root2",
                songs = emptyList(),
                torrentPath = "torrent2.torrent",
                isExclusive = false
            )
        )

        coEvery { database.dao.getAll() } returns albumEntities
        every { userTierVerifier.isProUser(any()) } returns true
        every { userTierVerifier.isUltimateUser(any()) } returns true

        val result = repository.getAlbumsFromCache(userPublicKey)

        assertEquals(2, result.size)
        assertEquals("Album 1", result[0].title)
        assertEquals("Album 2", result[1].title)
        coVerify {
            database.dao.getAll()
            userTierVerifier.isProUser(any())
        }
    }
}
