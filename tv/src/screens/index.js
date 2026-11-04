import { HomeScreen } from './home.js';
import { LiveScreen } from './live.js';
import { GuideScreen } from './guide.js';
import { VodScreen } from './vod.js';
import { DetailsScreen } from './details.js';
import { SearchScreen } from './search.js';
import { PlayerScreen } from './player.js';
import { MultiViewScreen } from './multiview.js';
import { SettingsScreen } from './settings.js';
import { PlaylistsScreen, AddPlaylistScreen } from './playlists.js';
import { ProfilesScreen } from './profiles.js';
import { AppsScreen } from './apps.js';
import { AboutScreen } from './about.js';

export const screens = {
  home: HomeScreen,
  live: LiveScreen,
  guide: GuideScreen,
  movies: (p, nav) => VodScreen(Object.assign({ kind: 'movie' }, p), nav),
  series: (p, nav) => VodScreen(Object.assign({ kind: 'series' }, p), nav),
  details: DetailsScreen,
  search: SearchScreen,
  player: PlayerScreen,
  multiview: MultiViewScreen,
  settings: SettingsScreen,
  playlists: PlaylistsScreen,
  addPlaylist: AddPlaylistScreen,
  profiles: ProfilesScreen,
  apps: AppsScreen,
  about: AboutScreen,
};
