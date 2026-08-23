ALTER TABLE recommendations
    ADD COLUMN recommendation_type VARCHAR(30) NOT NULL DEFAULT 'DOWNSIZE',
    ADD COLUMN confidence_level VARCHAR(20);
