package nl.tudelft.trustchain.musicdao.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import javax.inject.Inject

@Module
@InstallIn(ActivityComponent::class)
object ArtistModule {
    @Provides
    @ActivityScoped
    suspend fun provideCurrentArtist(artistRepository: ArtistRepository): Artist {
        return artistRepository.getMyself() ?: throw IllegalStateException("Current artist not found")
    }
}
