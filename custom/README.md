# Custom edition (built-in servers)

Edit `preset.json` in this folder and commit. The next build then also publishes
`ZVision-Custom-N.apk`, `ZVision-Custom-Samsung-N.wgt` and `ZVision-Custom-LG-N.ipk`.
Users of that edition only type their username and password.

```json
{
  "appName": "My TV",
  "providerName": "My IPTV",
  "allowOtherSources": false,
  "servers": [
    { "name": "Server 1", "url": "http://dns1.example.com:8080" },
    { "name": "Server 2", "url": "http://dns2.example.com:80" },
    { "name": "M3U", "type": "m3u", "url": "http://dns.example.com/get.php?username={username}&password={password}&type=m3u_plus&output=ts" }
  ]
}
```

- `appName`: name under the app icon.
- `providerName`: shown on the sign-in screen and as the playlist name.
- `servers`: one or more. `type` is `xtream` (default) or `m3u` (`{username}` / `{password}` are replaced). `epg` (optional) = XMLTV link.
- `allowOtherSources`: `true` keeps the normal "Add playlist" button too.

The custom edition installs next to the normal Z-Vision (different app id).
With an empty `servers` list nothing extra is built.

## Changing a server address without a new version

`servers.json` (this folder) is read by the installed apps at every start (`remoteUrl` in `preset.json`).
Edit it on GitHub and commit: within about 5 minutes, every app that is restarted uses the new addresses,
and users who were signed in on a server keep their login (servers are matched by **name**, so keep the names).
Editing only `servers.json` does not start a new build.
