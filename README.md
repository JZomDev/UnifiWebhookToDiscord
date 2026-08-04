# Unifi to Discord Webhook

Links:

[Link to docker hub](https://hub.docker.com/repository/docker/redzguilt/unifi-webhook-to-discord/general)

This is a tiny webhook listener that listens for Unifi's webhooks sent to the docker container's IP:PORT and formats the message to a Discord webhook.

You can add devices by using a device's mac address as a variable and the device name as the variable name.

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
