Download the PUBLIC CA certificate from the Overview / Connection information
of your own Aiven MySQL service. Save it in this directory as ca.pem.
Do not put passwords, private keys or connection URLs containing passwords here.
The certificate should start with -----BEGIN CERTIFICATE-----.
The free Render Dockerfile creates a truststore from it at build time.
When Aiven rotates the CA, update ca.pem and rebuild the image.
