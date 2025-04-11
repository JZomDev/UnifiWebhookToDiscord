# Unifi to Discord Webhook

This is a tiny webhook listener that listens for Unifi's webhooks sent to the docker container's IP:PORT and formats the message to a Discord webhook.

You can add devices by using a device's mac address as a variable and the device name as the variable name.

Example of a config:

![image](https://github.com/user-attachments/assets/a677f508-5be0-48c1-b318-840f83fdeba1)

You can add as many variables as you want. The variable name must be the Mac Address of the device you want alerts from, otherwise it will be called unknown device. 

The Mac Address can be found here:

![image](https://github.com/user-attachments/assets/9a8c5451-0cbe-44ba-bd15-475641497b49)

The Unifi set up is:

![image](https://github.com/user-attachments/assets/82b9e438-32c3-4b64-ab75-1bbd13bfbd6a)

The IP:PORT is the IP:PORT you set up for the docker container

When set up correctly you will see:

![image](https://github.com/user-attachments/assets/b67abc97-fa61-4cfb-9b93-7be64e50f0b5)
