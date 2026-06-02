create table if not exists llm_budget_usage (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    scene_id varchar(64) not null,
    usage_date date not null,
    provider varchar(128) not null,
    model varchar(128) not null,
    attempt_count bigint not null default 0,
    consumed_units numeric(12, 4) not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (tenant_id, scene_id, usage_date, provider, model)
);

create index if not exists idx_llm_budget_usage_tenant_scene_date
    on llm_budget_usage (tenant_id, scene_id, usage_date desc);
