alter table nlu_strategy
    add column if not exists model_policy jsonb not null default '{}'::jsonb;
