drop schema if exists "partya" cascade;
drop owned by "partya";
drop role "partya";
CREATE USER "partya" WITH LOGIN PASSWORD '123123';
CREATE SCHEMA "partya";
GRANT USAGE ON SCHEMA "partya" TO "partya";
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "partya" TO "partya";
ALTER DEFAULT privileges IN SCHEMA "partya" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "partya";
GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "partya" TO "partya";
ALTER DEFAULT privileges IN SCHEMA "partya" GRANT USAGE, SELECT ON sequences TO "partya";
ALTER ROLE "partya" SET search_path = "partya";
DROP TABLE IF EXISTS partya.user_roles;
DROP TABLE IF EXISTS partya.roles_permissions;
DROP TABLE IF EXISTS partya.users;

CREATE TABLE IF NOT EXISTS partya.users
(
   username character varying(255),
   password text NOT NULL,
   CONSTRAINT pk1_users PRIMARY KEY (username)
);

CREATE TABLE IF NOT EXISTS partya.roles_permissions
(
   role_name character varying(255),
   permission text,
   CONSTRAINT pk1_roles_permissions PRIMARY KEY (role_name, permission)
) ;

CREATE TABLE IF NOT EXISTS partya.user_roles
(
   username character varying(255),
   role_name character varying(255),
   CONSTRAINT fk1_user_role FOREIGN KEY (username) REFERENCES partya.users (username) ON UPDATE CASCADE ON DELETE CASCADE
);
insert into partya.users(username,password) values ('user1','$shiro1$SHA-256$500000$CzG5ppBuiHHrnCGptezTyQ==$XV0FKXW8uHsnqqTusEpBdV+RLDAjTkyEyh0XgiRF9Co=');
insert into partya.roles_permissions(role_name,permission) values ('admin','ALL');
insert into partya.user_roles(username,role_name) values ('user1','admin');


drop schema if exists "partyb" cascade;
drop owned by "partyb";
drop role "partyb";
CREATE USER "partyb" WITH LOGIN PASSWORD '123123';
CREATE SCHEMA "partyb";
GRANT USAGE ON SCHEMA "partyb" TO "partyb";
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "partyb" TO "partyb";
ALTER DEFAULT privileges IN SCHEMA "partyb" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "partyb";
GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "partyb" TO "partyb";
ALTER DEFAULT privileges IN SCHEMA "partyb" GRANT USAGE, SELECT ON sequences TO "partyb";
ALTER ROLE "partyb" SET search_path = "partyb";


drop schema if exists "notary" cascade;
drop owned by "notary";
drop role "notary";
CREATE USER "notary" WITH LOGIN PASSWORD '123123';
CREATE SCHEMA "notary";
GRANT USAGE ON SCHEMA "notary" TO "notary";
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "notary" TO "notary";
ALTER DEFAULT privileges IN SCHEMA "notary" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "notary";
GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "notary" TO "notary";
ALTER DEFAULT privileges IN SCHEMA "notary" GRANT USAGE, SELECT ON sequences TO "notary";
ALTER ROLE "notary" SET search_path = "notary";


GRANT ALL ON SCHEMA "notary" TO "notary";
GRANT ALL ON SCHEMA "partya" TO "partya";
GRANT ALL ON SCHEMA "partyb" TO "partyb";
CREATE SEQUENCE notary.hibernate_sequence INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 8 CACHE 1 NO CYCLE;
CREATE SEQUENCE partya.hibernate_sequence INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 8 CACHE 1 NO CYCLE;
CREATE SEQUENCE partyb.hibernate_sequence INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 8 CACHE 1 NO CYCLE;
