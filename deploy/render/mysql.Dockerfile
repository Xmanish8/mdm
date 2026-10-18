FROM mysql:8.4
COPY src/main/resources/schema.sql /docker-entrypoint-initdb.d/01-schema.sql
COPY deploy/02-permissions.sh /opt/mdm/permissions.sh
COPY deploy/render/02-initialize.sh /docker-entrypoint-initdb.d/02-initialize.sh
RUN mkdir -p /run/secrets \
    && render_group="$(getent group 1000 | cut -d: -f1)" \
    && if [ -z "$render_group" ]; then groupadd -g 1000 rendersecrets; render_group=rendersecrets; fi \
    && usermod -a -G "$render_group" mysql \
    && ln -s /etc/secrets/root.password /run/secrets/root.password \
    && ln -s /etc/secrets/provision.password /run/secrets/provision.password \
    && sed -i 's/\r$//' /opt/mdm/permissions.sh /docker-entrypoint-initdb.d/02-initialize.sh \
    && chmod 0644 /opt/mdm/permissions.sh /docker-entrypoint-initdb.d/02-initialize.sh
ENV MYSQL_DATABASE=mdm \
    MYSQL_USER=mdm_app \
    MYSQL_ROOT_HOST=localhost \
    MYSQL_PASSWORD_FILE=/etc/secrets/db.password \
    MYSQL_ROOT_PASSWORD_FILE=/etc/secrets/root.password
CMD ["mysqld", "--require-secure-transport=ON", "--local-infile=OFF"]
