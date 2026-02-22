#!/usr/bin/env bash

if [ -z "$ADMIN_PASSWORD" ]; then
  echo "Environment variable ADMIN_PASSWORD not set. See https://docs.datomic.com/on-prem/configuring-embedded.html#sec-2-1"
  exit 1
fi

if [ -z "$DATOMIC_PASSWORD" ]; then
  echo "Environment variable DATOMIC_PASSWORD not set. See https://docs.datomic.com/on-prem/configuring-embedded.html#sec-2-1"
  exit 1
fi

sed -i "/host=datomic/a alt-host=${ALT_HOST:-127.0.0.1}" /datomic/transactor.properties
sed -i "s/# storage-admin-password=/storage-admin-password=${ADMIN_PASSWORD}/" /datomic/transactor.properties
sed -i "s/# storage-datomic-password=/storage-datomic-password=${DATOMIC_PASSWORD}/" /datomic/transactor.properties

if [ -n "$ADMIN_PASSWORD_OLD" ]; then
  sed -i "s/# old-storage-admin-password=/old-storage-admin-password=$ADMIN_PASSWORD_OLD/" /datomic/transactor.properties
fi

if [ -n "$DATOMIC_PASSWORD_OLD" ]; then
  sed -i "s/# old-storage-datomic-password=/old-storage-datomic-password=$DATOMIC_PASSWORD_OLD/" /datomic/transactor.properties
fi

sed -i "s/# encrypt-channel=true/encrypt-channel=${ENCRYPT_CHANNEL:-true}/" /datomic/transactor.properties

/datomic/bin/transactor /datomic/transactor.properties
