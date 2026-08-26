CREATE TABLE job_categories (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES job_categories(id),
    slug VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    fulfillment_mode VARCHAR(24),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_job_category_slug CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_job_category_name CHECK (length(trim(name)) >= 2),
    CONSTRAINT ck_job_category_fulfillment_mode CHECK (
        fulfillment_mode IS NULL OR fulfillment_mode IN ('ON_SITE', 'POINT_TO_POINT')
    ),
    CONSTRAINT ck_job_category_parent_not_self CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX idx_job_categories_parent_sort ON job_categories(parent_id, sort_order, name);
CREATE INDEX idx_job_categories_active_parent ON job_categories(active, parent_id);

ALTER TABLE jobs ADD COLUMN category_id BIGINT REFERENCES job_categories(id);
CREATE INDEX idx_jobs_category_status_created ON jobs(category_id, status, created_at DESC);

INSERT INTO job_categories (slug, name, fulfillment_mode, sort_order) VALUES
('transport-przeprowadzki', 'Transport i przeprowadzki', NULL, 10),
('paczki-kurier', 'Paczki i kurier', NULL, 20),
('zakupy-dostawy', 'Zakupy i dostawy', NULL, 30),
('dom-remont', 'Dom i remont', NULL, 40),
('sprzatanie', 'Sprzątanie', NULL, 50),
('ogrod', 'Ogród i teren zewnętrzny', NULL, 60),
('zwierzeta', 'Zwierzęta', NULL, 70),
('technologia', 'Technologia i pomoc techniczna', NULL, 80),
('sprawy-codzienne', 'Sprawy codzienne', NULL, 90),
('utylizacja', 'Wywóz i utylizacja', NULL, 100),
('pojazdy', 'Pomoc przy pojazdach', NULL, 110);

INSERT INTO job_categories (parent_id, slug, name, fulfillment_mode, sort_order)
SELECT id, 'pelna-przeprowadzka', 'Pełna przeprowadzka', 'POINT_TO_POINT', 10 FROM job_categories WHERE slug='transport-przeprowadzki'
UNION ALL SELECT id, 'transport-mebli-agd', 'Transport mebli i AGD', 'POINT_TO_POINT', 20 FROM job_categories WHERE slug='transport-przeprowadzki'
UNION ALL SELECT id, 'wnoszenie-wynoszenie', 'Wnoszenie i wynoszenie', 'ON_SITE', 30 FROM job_categories WHERE slug='transport-przeprowadzki'
UNION ALL SELECT id, 'pomoc-zaladunek', 'Pomoc przy załadunku i rozładunku', 'ON_SITE', 40 FROM job_categories WHERE slug='transport-przeprowadzki'
UNION ALL SELECT id, 'dokumenty', 'Dokumenty', 'POINT_TO_POINT', 10 FROM job_categories WHERE slug='paczki-kurier'
UNION ALL SELECT id, 'mala-paczka', 'Mała paczka', 'POINT_TO_POINT', 20 FROM job_categories WHERE slug='paczki-kurier'
UNION ALL SELECT id, 'duza-paczka', 'Duża paczka', 'POINT_TO_POINT', 30 FROM job_categories WHERE slug='paczki-kurier'
UNION ALL SELECT id, 'odbior-przesylki', 'Odbiór i doręczenie przesyłki', 'POINT_TO_POINT', 40 FROM job_categories WHERE slug='paczki-kurier'
UNION ALL SELECT id, 'zakupy-spozywcze', 'Zakupy spożywcze', 'POINT_TO_POINT', 10 FROM job_categories WHERE slug='zakupy-dostawy'
UNION ALL SELECT id, 'zakupy-sklep', 'Zakupy w sklepie', 'POINT_TO_POINT', 20 FROM job_categories WHERE slug='zakupy-dostawy'
UNION ALL SELECT id, 'odbior-ze-sklepu', 'Odbiór ze sklepu', 'POINT_TO_POINT', 30 FROM job_categories WHERE slug='zakupy-dostawy'
UNION ALL SELECT id, 'jedzenie-restauracja', 'Odbiór jedzenia z restauracji', 'POINT_TO_POINT', 40 FROM job_categories WHERE slug='zakupy-dostawy'
UNION ALL SELECT id, 'malowanie', 'Malowanie', 'ON_SITE', 10 FROM job_categories WHERE slug='dom-remont'
UNION ALL SELECT id, 'montaz-mebli', 'Montaż mebli', 'ON_SITE', 20 FROM job_categories WHERE slug='dom-remont'
UNION ALL SELECT id, 'zlota-raczka', 'Złota rączka', 'ON_SITE', 30 FROM job_categories WHERE slug='dom-remont'
UNION ALL SELECT id, 'hydraulik', 'Hydraulika', 'ON_SITE', 40 FROM job_categories WHERE slug='dom-remont'
UNION ALL SELECT id, 'elektryk', 'Elektryka', 'ON_SITE', 50 FROM job_categories WHERE slug='dom-remont'
UNION ALL SELECT id, 'drobny-remont', 'Drobny remont', 'ON_SITE', 60 FROM job_categories WHERE slug='dom-remont'
UNION ALL SELECT id, 'sprzatanie-mieszkania', 'Sprzątanie mieszkania lub domu', 'ON_SITE', 10 FROM job_categories WHERE slug='sprzatanie'
UNION ALL SELECT id, 'sprzatanie-biura', 'Sprzątanie biura', 'ON_SITE', 20 FROM job_categories WHERE slug='sprzatanie'
UNION ALL SELECT id, 'sprzatanie-po-remoncie', 'Sprzątanie po remoncie', 'ON_SITE', 30 FROM job_categories WHERE slug='sprzatanie'
UNION ALL SELECT id, 'mycie-okien', 'Mycie okien', 'ON_SITE', 40 FROM job_categories WHERE slug='sprzatanie'
UNION ALL SELECT id, 'koszenie-trawy', 'Koszenie trawy', 'ON_SITE', 10 FROM job_categories WHERE slug='ogrod'
UNION ALL SELECT id, 'pielegnacja-ogrodu', 'Pielęgnacja ogrodu', 'ON_SITE', 20 FROM job_categories WHERE slug='ogrod'
UNION ALL SELECT id, 'porzadki-zewnetrzne', 'Porządki na zewnątrz', 'ON_SITE', 30 FROM job_categories WHERE slug='ogrod'
UNION ALL SELECT id, 'spacer-z-psem', 'Spacer z psem', 'ON_SITE', 10 FROM job_categories WHERE slug='zwierzeta'
UNION ALL SELECT id, 'opieka-zwierze', 'Opieka nad zwierzęciem', 'ON_SITE', 20 FROM job_categories WHERE slug='zwierzeta'
UNION ALL SELECT id, 'transport-zwierzecia', 'Transport zwierzęcia', 'POINT_TO_POINT', 30 FROM job_categories WHERE slug='zwierzeta'
UNION ALL SELECT id, 'pomoc-komputer', 'Pomoc z komputerem', 'ON_SITE', 10 FROM job_categories WHERE slug='technologia'
UNION ALL SELECT id, 'konfiguracja-sprzetu', 'Konfiguracja sprzętu', 'ON_SITE', 20 FROM job_categories WHERE slug='technologia'
UNION ALL SELECT id, 'domowa-siec', 'Sieć domowa i Wi‑Fi', 'ON_SITE', 30 FROM job_categories WHERE slug='technologia'
UNION ALL SELECT id, 'zalatwienie-sprawy', 'Załatwienie sprawy', 'ON_SITE', 10 FROM job_categories WHERE slug='sprawy-codzienne'
UNION ALL SELECT id, 'stanie-w-kolejce', 'Stanie w kolejce', 'ON_SITE', 20 FROM job_categories WHERE slug='sprawy-codzienne'
UNION ALL SELECT id, 'odbior-dokumentow', 'Odbiór dokumentów lub rzeczy', 'POINT_TO_POINT', 30 FROM job_categories WHERE slug='sprawy-codzienne'
UNION ALL SELECT id, 'pomoc-na-miejscu', 'Pomoc na miejscu', 'ON_SITE', 40 FROM job_categories WHERE slug='sprawy-codzienne'
UNION ALL SELECT id, 'wywoz-rzeczy', 'Wywóz niepotrzebnych rzeczy', 'POINT_TO_POINT', 10 FROM job_categories WHERE slug='utylizacja'
UNION ALL SELECT id, 'wynoszenie-odpadow', 'Wynoszenie odpadów', 'ON_SITE', 20 FROM job_categories WHERE slug='utylizacja'
UNION ALL SELECT id, 'awaryjna-pomoc-auto', 'Drobna pomoc przy samochodzie', 'ON_SITE', 10 FROM job_categories WHERE slug='pojazdy'
UNION ALL SELECT id, 'odbior-czesci-auto', 'Odbiór części samochodowych', 'POINT_TO_POINT', 20 FROM job_categories WHERE slug='pojazdy';
