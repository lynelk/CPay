SET UP
=================================
1. Create deployment directory
```
mkdir /opt/cpay
```
2. sudo mkdir -p /opt/cpay/bin
# Change ownership to the user running the service (e.g., cpay or root)
sudo chown -R root:root /opt/cpay

3. Create the .env file.
```
/etc/cpay/.env
```

4. # Create a Systemd service
```
[Unit]
Description=CPay Core Payments Gateway Backend Engine
After=syslog.target network.target mysqld.service

[Service]
User=root
# The runtime working directory for logs/relative paths
WorkingDirectory=/opt/cpay
# Pointing directly to the isolated production jar location
ExecStart=/usr/bin/java -jar /opt/cpay/bin/cito-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=10
EnvironmentFile=/etc/cpay/.env

[Install]
WantedBy=multi-user.target

```

5. # Create Lock Managment
```
sudo mkdir -p /var/opt/cpay/locks
sudo chmod 755 /var/opt/cpay/locks
```