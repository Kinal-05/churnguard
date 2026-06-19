-- Demo tenant for local development / portfolio demo purposes.
-- The api_key_hash here is a placeholder bcrypt hash of 'demo-api-key-12345'
-- (tenant API key auth is used by the /api/v1/events ingestion endpoint).
INSERT INTO tenants (id, name, api_key_hash, created_at)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Acme SaaS Inc.',
    '$2b$12$TeC6upQvON.UoUbJleLA3ulu/UDuuhX5Vja/vSjnCe1Z1n96vig6u',
    now()
);

-- Demo admin login: email admin@acme.test / password admin123
INSERT INTO app_users (id, tenant_id, email, password_hash, role, created_at)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'admin@acme.test',
    '$2b$12$TeC6upQvON.UoUbJleLA3ulu/UDuuhX5Vja/vSjnCe1Z1n96vig6u',
    'ADMIN',
    now()
);

-- Sample customers spanning healthy, at-risk, and churned profiles
INSERT INTO customers (id, tenant_id, external_ref, name, email, plan, mrr_cents, signup_date, status, created_at)
VALUES
    ('33333333-3333-3333-3333-333333333331', '11111111-1111-1111-1111-111111111111', 'CUST-1001', 'Bluebird Logistics', 'ops@bluebird.test', 'PRO', 49900, '2024-02-10', 'ACTIVE', now()),
    ('33333333-3333-3333-3333-333333333332', '11111111-1111-1111-1111-111111111111', 'CUST-1002', 'Northwind Traders', 'admin@northwind.test', 'ENTERPRISE', 199900, '2023-11-01', 'ACTIVE', now()),
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'CUST-1003', 'Quantum Retail', 'it@quantumretail.test', 'STARTER', 9900, '2025-01-15', 'ACTIVE', now()),
    ('33333333-3333-3333-3333-333333333334', '11111111-1111-1111-1111-111111111111', 'CUST-1004', 'Solstice Media', 'team@solstice.test', 'PRO', 49900, '2024-06-20', 'ACTIVE', now()),
    ('33333333-3333-3333-3333-333333333335', '11111111-1111-1111-1111-111111111111', 'CUST-1005', 'Harbor Analytics', 'support@harbor.test', 'ENTERPRISE', 299900, '2023-05-05', 'CHURNED', now());

-- Sample events: Bluebird (healthy, frequent logins), Quantum Retail (declining usage + payment failure = at risk)
INSERT INTO customer_events (customer_id, event_type, event_payload, occurred_at)
VALUES
    ('33333333-3333-3333-3333-333333333331', 'LOGIN', '{}', now() - interval '1 day'),
    ('33333333-3333-3333-3333-333333333331', 'LOGIN', '{}', now() - interval '3 days'),
    ('33333333-3333-3333-3333-333333333331', 'FEATURE_USE', '{"feature": "reporting"}', now() - interval '2 days'),
    ('33333333-3333-3333-3333-333333333333', 'LOGIN', '{}', now() - interval '45 days'),
    ('33333333-3333-3333-3333-333333333333', 'PAYMENT_FAILED', '{"reason": "card_declined"}', now() - interval '20 days'),
    ('33333333-3333-3333-3333-333333333333', 'SUPPORT_TICKET', '{"subject": "Cannot export data"}', now() - interval '15 days'),
    ('33333333-3333-3333-3333-333333333333', 'SUPPORT_TICKET', '{"subject": "Billing question"}', now() - interval '10 days');