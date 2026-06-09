alter table config_version
    add column if not exists approved_snapshot_hash varchar(128);
