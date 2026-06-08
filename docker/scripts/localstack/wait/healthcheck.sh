#!/usr/bin/env bash
queues=$(awslocal sqs list-queues)
echo $queues | grep "coordinator-queue" || exit 1
