# Unifi to Discord Webhook

Links:

[Link to docker hub](https://hub.docker.com/repository/docker/redzguilt/unifi-webhook-to-discord/general)

This is a tiny webhook listener that listens for Unifi's webhooks sent to the docker container's IP:PORT and formats the message to a Discord webhook.

## Running the container

| Setting | Value |
| --- | --- |
| Web UI port | `45462` (override with env `webui_port`) |
| Webhook listener port | `8080` inside the container (override with env `port`; not changeable in the UI) |
| Config volume | `/config` (settings, cameras and the password hash), **required** for settings to persist |

You must map a host folder to `/config`, otherwise your settings, cameras and password are lost whenever the container is updated or recreated (on Unraid that includes every image update and every template edit). The image does not create a volume for you.

On Unraid, add a Path mapping from container path `/config` to a host path such as `/mnt/user/appdata/unifi-webhook-to-discord`, plus port mappings for `45462` (web UI) and the webhook listener: the Unraid template maps host port `7019` (default, change it as you like) to container port `8080`. In UniFi, set the webhook delivery URL to `http://<host-ip>:<host-port>/data`, for example `http://192.168.1.132:7019/data`. The container runs as `99:100`, so the host directory must be writable by that user, for example `chown 99:100 /path/to/config`.

If `/config` is not a mapped volume, or is not writable, the app still runs and the UI shows a red warning naming the config directory: settings are kept only inside the container and are lost on update or recreate. In that state the legacy environment variables keep working as before (they are imported on every start), and the environment-variable banner asks you to keep them until you have mapped the volume. Once `/config` is mapped and writable the warning disappears.

Log files are written to `logs/UnifiWebhookToDiscord.log` in the same folder (last 5 days kept, `INFO` and above); the console output is still available through `docker logs`. So the single `/config` mapping covers both settings and logs.

If `settings.json` cannot be read (for example it was corrupted), the app moves it aside as `settings.json.corrupt-<timestamp>` in the same folder, starts with default settings (login `admin` / `admin` again) and shows a notice in the UI. Nothing is overwritten, so you can inspect or restore the old file.

```
docker run -d -p 45462:45462 -p 8080:8080 -v /path/to/config:/config redzguilt/unifi-webhook-to-discord
```

## Web UI

Open `http://<host>:45462`. The first login is `admin` / `admin`; you are forced to choose a new password straight away.

- **Settings**: the Discord webhook URL, the Unifi console UUIDs (optional; paste any `https://unifi.ui.com/consoles/<id>:<number>/...` link into "UniFi console link" and click "Fill UUIDs" to fill both boxes), and a button that sends a test message to Discord. The page also shows which port the webhook listener runs on; that port is set only with the `port` environment variable (see below), because it is the container side of your Docker port mapping. If the listener could not start (for example the port is in use), every page shows a red notice with the reason.
- **Cameras**: add a camera by its MAC address and a display name. Unknown MAC addresses appear in Discord as "unknown device".
- **Guide**: renders `guides/setup.md`, the setup walkthrough.

## Environment variables (optional, import only)

Environment variables are no longer required. Each of these is imported once into the UI settings the first time it is seen (it never overwrites a value you already set in the UI):

- `webhook`: Discord webhook URL
- `UUID_1`, `UUID_2`: Unifi console UUIDs
- MAC address variables (for example `AA:BB:CC:11:22:33=Front`): imported as cameras, with the value as the name

Further variables configure the app itself and are read on every start (they are not imported and are not listed in the banner): `port` (webhook listener port inside the container, default `8080`; must be 1-65535 and differ from the web UI port, otherwise `8080` is used and a warning is logged; if you change it, change the container side of your port mapping to match), `webui_port` (web UI port, default `45462`), `config_dir` (settings directory, default `/config`) and `config_require_mount` (set to `true` in the image; warns when the settings directory is not a mounted volume).

While any of the import variables are still set, the UI shows a banner listing them. Dismiss it with the x for the current page, or turn it off permanently with the "Env variable reminder" checkbox on the Settings page. Once `/config` is mapped and the values are imported, you can remove the variables from your container template; while settings are not persistent, keep them. A camera you delete in the UI is not re-imported, even if its MAC variable is still set.

## Original variable-based setup

The Cameras page in the web UI is the preferred way to add devices now; the variable approach below still works as an import.

You can add devices by using a device's mac address as a variable and the device name as the variable value.

Example of a config:

![image](https://github.com/user-attachments/assets/a677f508-5be0-48c1-b318-840f83fdeba1)

You can add as many variables as you want. The variable name must be the Mac Address of the device you want alerts from, otherwise it will be called unknown device. 

The Mac Address can be found here:

![image](https://github.com/user-attachments/assets/9a8c5451-0cbe-44ba-bd15-475641497b49)

The Unifi set up is (optional thumbnails)

![image](https://github.com/user-attachments/assets/f10c05ea-cbbf-4032-9e72-1ea0327c2eb0)


The IP:PORT is your Docker host's IP and the host port mapped to the listener, for example `http://192.168.1.132:7019/data`.

When set up correctly you will see:

![image](https://github.com/user-attachments/assets/7bcb425b-4d50-47e0-97e8-3e6f10a4ea3d)
