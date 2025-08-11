#!/bin/bash

export HOSTNAME="CLUSTER-ID.dsql-fnh4.us-east-1.on.aws"
export PGHOSTADDR="127.0.0.1"
export PGPASSWORD=$(aws dsql generate-db-connect-admin-auth-token --hostname $HOSTNAME)
export PGSSLMODE="require"

ssh -fN jumpbox  

psql -d postgres -h $HOSTNAME -p 5432 -U admin -c '\conninfo'

echo "test env setup, run 'ssh -O exit jumpbox' when done"

# to kill the tunnel:
#ssh -O exit jumpbox
