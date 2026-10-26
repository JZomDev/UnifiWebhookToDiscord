# Unifi to Discord Webhook

Links:

[Link to docker hub](https://hub.docker.com/repository/docker/redzguilt/unifi-webhook-to-discord/general)

This is a tiny webhook listener that listens for Unifi's webhooks sent to the docker container's IP:PORT and formats the message to a Discord webhook.

## Running the container

| Setting | Value |
| --- | --- |
| Web UI port | `45462` (override with env `webui_port`) |
| Webhook listener port | `8080` by default (changeable in Settings) |
| Config volume | `/config` (settings, cameras and the password hash) |

On Unraid, add a path mapping from container path `/config` to a host path such as `/mnt/user/appdata/unifi-webhook-to-discord`, plus port mappings for `45462` (web UI) and `8080` (webhook listener). The container runs as `99:100`, so a bind-mounted host directory must be writable by that user (`chown 99:100 /path/to/config`; Unraid appdata shares already are). A root-owned host directory leaves the app running but not persisting: the UI shows a warning, and your settings and changed password are lost on every restart.

```
docker run -d -p 45462:45462 -p 8080:8080 -v /path/to/config:/config redzguilt/unifi-webhook-to-discord
```

## Web UI

Open `http://<host>:45462`. The first login is `admin` / `admin`; you are forced to choose a new password straight away.

- **Settings**: the Discord webhook URL, the webhook listen port, the Unifi console UUIDs (optional), and a button that sends a test message to Discord. The listen port must match the container port you map (for example listen port `8080` with a `8080` mapping).
- **Cameras**: add a camera by its MAC address and a display name. Unknown MAC addresses appear in Discord as "unknown device".
- **Guide**: renders `guides/setup.md`, the setup walkthrough.

## Environment variables (optional, import only)

Environment variables are no longer required. Each of these is imported once into the UI settings the first time it is seen (it never overwrites a value you already set in the UI):

- `webhook`: Discord webhook URL
- `port`: webhook listener port
- `UUID_1`, `UUID_2`: Unifi console UUIDs
- MAC address variables (for example `AA:BB:CC:11:22:33=Front`): imported as cameras, with the value as the name

Two further variables configure the app itself: `webui_port` (web UI port, default `45462`) and `config_dir` (settings directory, default `/config`).

While any of the import variables are still set, the UI shows a banner listing them. Dismiss it with the x for the current page, or turn it off permanently with the "Env variable reminder" checkbox on the Settings page. You can remove the variables from your container template once they are imported. A camera you delete in the UI is not re-imported, even if its MAC variable is still set.

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


The IP:PORT is the IP:PORT you set up for the docker container

When set up correctly you will see:

![image](https://github.com/user-attachments/assets/7bcb425b-4d50-47e0-97e8-3e6f10a4ea3d)
