alter table config_version
    add column if not exists approved_by varchar(128),
    add column if not exists approved_at timestamptz;
