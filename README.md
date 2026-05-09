## CPay — Core Payments Service Engine

CPay is the core payments orchestration service for the CitoTech stack.
[CitoConnect](https://github.com/lynelk/citoconnect) integrates CPay as
its **Core Payments Service Engine**, which means every payment
integration and channel inside CitoConnect — MTN MoMo, Airtel Money,
Safaricom M-Pesa, Yo! Payments, Stripe, Flutterwave, Pesapal — is
dispatched through CPay's `/api/v1` REST surface or through CPay-owned
adapter modules. See `docs/citoconnect-integration.md` for the
integration contract and the canonical request/response shapes.

## Available Scripts
Prerequisite
============================
1. Java jdk Version >= 8
3. MySQL

Installation
============================
1. cd to clientside
2. Create and import the data at clientside/db/structure.sql
3. Inport currently applied db changes: clientside/db/db_changes.sql
4. Inport initial admin user at clientside/db/initialize.sql
5. Initial Username & Password are joseph.tabajjwa@gmail.com : @cpayadmin@domain
6. cd to setup directory
7. run ./install.sh

Starting the servers
=============================
1. Run: /etc/init.d/cpayadmin/start.sh | /home/centos/cpay/setup/start.sh

Restart the server
==============================
1. Run: /etc/init.d/cpayadmin/restart.sh | /home/centos/cpay/setup/restart.sh


Stop the servers
================================
1. Run: /etc/init.d/cpayadmin/shutdown.sh | /home/centos/cpay/setup/shutdown.sh


Ports
===================
1. Java: 443

Logs
=========================
1. React: /tmp/cpayadmin.log
2. Cpay-Java: /var/log/cpayadmin/log.txt


Compiling React App and Java 
=============================
1. cd into ../clientside directory.
2. run the command: npm run build.
2.1. Copy the following to the head section of the ../clientside/build/index.html

<style>.loader:empty {position: absolute;top: calc(50% - 4em);left: calc(50% - 4em);width: 6em;height: 6em;border: 1.1em solid rgba(0, 0, 0, 0.2);border-left: 1.1em solid #000000;border-radius: 50%;animation: load8 1.1s infinite linear;}@keyframes load8 {0% {transform: rotate(0deg);}100% {transform: rotate(360deg);}}</style><script>function onLoad() {var loader = document.getElementById("cpay_loader");loader.className = "";}</script>

2.2. Copy the following content to the body section of the ../clientside/build/index.html

<div id="cpay_loader" class="loader"></div>

2.3. Include the following content to body tag of ../clientside/build/index.html
onload="onLoad();"

2.4 Change <link rel="icon" href="/favicon.ico"/> to <link rel="icon" href="/favicon.png"/>

2.5 Change title to: CPay 

3. Copy all contents of .../build/ to ../InitializrSpringbootProject/src/main/resources/static/

Use the Following Link to General PKCS12 version of the SSL CERTIFICATE
https://dzone.com/articles/spring-boot-secured-by-lets-encrypt


INSTALLING AND RENEWING CERTIFICATES
============================================
1. Login to the Lightsail server.
2. Stop any service running on Port 80.
3. RUN: sudo certbot renew | sudo certbot certonly -a standalone -d cpaytest.citotech.net
4. Convert the updated certificate to PKCS12: 

openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -out springboot_letsencrypt.p12 -name bootalias -CAfile chain.pem  -caname centos
Password: cpayadmin

Then for Kwiff:
/home/centos/kwiff/setup/springboot_letsencrypt.p12

Then for CpayTest:
openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -out springboot_letsencrypt.p12 -name bootalias -CAfile chain.pem  -caname cpayadmin
Password: cpayadmin

Then Restart the server: /etc/init.d/cpayadmin/restart.sh | supervisorctl restart cpaytest

5. Download the certificate and save it under: /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/cpay/InitializrSpringbootProject/src/main/resources/keystore

scp -i Keys/Lightsail/LightsailDefaultKey-eu-central-1.pem centos@18.190.63.205:/home/centos/springboot_letsencrypt.p12 /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/cpay/InitializrSpringbootProject/src/main/resources/keystore/.




Copy the new version to Cpay Server
====================================
scp -i /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/newcpay/new_cpay.pem /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/cpay/InitializrSpringbootProject/target/cito-0.0.1-SNAPSHOT.jar centos@18.190.63.205:/home/centos/cpay/setup/


scp -i /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/newcpay/new_cpay.pem /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/cpay/InitializrSpringbootProject/target/cito-0.0.1-SNAPSHOT.jar centos@18.190.63.205:/home/centos/kwiff/setup/

scp -i /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/newcpay/new_cpay.pem /Users/josephtabajjwa/Desktop/Joe/projects/CitoTech/paymentgw/cpay/InitializrSpringbootProject/target/cito-0.0.1-SNAPSHOT.jar centos@18.190.63.205:/home/centos/peaky/setup/

Compiling with Maven
=========================================
Run the command: maven package
It will package for you a jar file.





Remote Port Forwarding
=========================================
- Use the following link to process:
https://www.ssh.com/academy/ssh/tunneling/example

- ssh -i LightsailDefaultPrivateKey-eu-central-1.pem ubuntu@18.196.18.46 -R 8080:localhost:9000