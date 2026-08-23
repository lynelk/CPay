FROM mysql:8.4

# CPay migration V28 creates append-only audit triggers. Managed MySQL installations with binary
# logging enabled require this server capability unless the migration user has SUPER-equivalent
# trigger-creation privileges. Keep the official MySQL entrypoint so fresh datadirs are initialized
# normally; only add the supported server argument.
CMD ["mysqld", "--log-bin-trust-function-creators=1"]
