create table if not exists config_version (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    scene_id varchar(64) not null,
    version varchar(64) not null,
    status varchar(32) not null,
    description varchar(512),
    created_by varchar(128),
    created_at timestamptz not null default now(),
    published_at timestamptz,
    unique (tenant_id, scene_id, version)
);

create table if not exists intent_definition (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    scene_id varchar(64) not null,
    version varchar(64) not null,
    intent_code varchar(128) not null,
    intent_name varchar(256) not null,
    enabled boolean not null default true,
    definition jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique (tenant_id, scene_id, version, intent_code)
);

create table if not exists slot_definition (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    scene_id varchar(64) not null,
    version varchar(64) not null,
    intent_code varchar(128) not null,
    slot_code varchar(128) not null,
    required boolean not null default false,
    definition jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique (tenant_id, scene_id, version, intent_code, slot_code)
);

create table if not exists synonym_mapping (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    scene_id varchar(64) not null,
    version varchar(64) not null,
    term varchar(256) not null,
    normalized_term varchar(256) not null,
    created_at timestamptz not null default now()
);

create table if not exists nlu_strategy (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    scene_id varchar(64) not null,
    version varchar(64) not null,
    strategy_code varchar(128) not null,
    confidence_threshold numeric(4, 3) not null default 0.600,
    llm_policy jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique (tenant_id, scene_id, version, strategy_code)
);

create table if not exists scene_routing_rule (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    scene_id varchar(64) not null,
    version varchar(64) not null,
    route_stage varchar(32) not null,
    priority integer not null default 0,
    match_condition jsonb not null default '{}'::jsonb,
    route_target varchar(256) not null,
    created_at timestamptz not null default now()
);

create table if not exists downstream_action (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    scene_id varchar(64) not null,
    version varchar(64) not null,
    action_code varchar(128) not null,
    action_type varchar(64) not null,
    target varchar(512) not null,
    idempotency_required boolean not null default false,
    timeout_ms integer not null default 3000,
    action_schema jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique (tenant_id, scene_id, version, action_code)
);

create table if not exists recognition_trace (
    id bigserial primary key,
    trace_id varchar(128) not null,
    request_id varchar(128) not null,
    tenant_id varchar(64) not null,
    scene_id varchar(64),
    input_type varchar(32) not null,
    input_snapshot jsonb not null default '{}'::jsonb,
    intent_code varchar(128),
    decision varchar(32) not null,
    confidence numeric(5, 4) not null,
    slots_snapshot jsonb not null default '{}'::jsonb,
    recognition_path jsonb not null default '[]'::jsonb,
    downstream_action_code varchar(128),
    idempotency_key varchar(128),
    created_at timestamptz not null default now()
);

create index if not exists idx_recognition_trace_trace_id on recognition_trace (trace_id);
create index if not exists idx_recognition_trace_tenant_created on recognition_trace (tenant_id, created_at desc);

create table if not exists bad_case (
    id bigserial primary key,
    trace_id varchar(128) not null,
    request_id varchar(128) not null,
    tenant_id varchar(64) not null,
    scene_id varchar(64),
    intent_code varchar(128),
    decision varchar(32) not null,
    confidence numeric(5, 4) not null,
    reason varchar(512),
    input_snapshot jsonb not null default '{}'::jsonb,
    status varchar(32) not null default 'OPEN',
    created_at timestamptz not null default now()
);

create index if not exists idx_bad_case_tenant_status on bad_case (tenant_id, status, created_at desc);

create table if not exists idempotency_record (
    id bigserial primary key,
    idempotency_key varchar(128) not null unique,
    tenant_id varchar(64) not null,
    request_id varchar(128) not null,
    request_hash varchar(128) not null,
    action_code varchar(128) not null,
    action_type varchar(64) not null,
    target varchar(512) not null,
    status varchar(32) not null,
    retry_count integer not null default 0,
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_idempotency_tenant_request on idempotency_record (tenant_id, request_id);

create table if not exists audit_log (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    scene_id varchar(64),
    actor varchar(128),
    action varchar(128) not null,
    target_type varchar(128) not null,
    target_id varchar(128),
    detail jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
