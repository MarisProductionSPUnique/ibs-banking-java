#!/bin/sh
set -eu
if [ -n "${TS_AUTHKEY:-}" ]; then
  tailscaled --tun=userspace-networking --state=mem: --socket=/tmp/ibs-tailscale.sock --socks5-server=127.0.0.1:1055 &
  i=0
  while [ ! -S /tmp/ibs-tailscale.sock ] && [ "$i" -lt 20 ]; do
    sleep 1
    i=$((i+1))
  done
  if timeout 30 tailscale --socket=/tmp/ibs-tailscale.sock up --auth-key="$TS_AUTHKEY" --hostname="${TS_HOSTNAME:-ibs-render-appserver}" --accept-dns=false --accept-routes=false; then
    echo "IBS Tailscale authentication completed"
  else
    echo "IBS Tailscale unavailable; database connectivity not established" >&2
  fi
fi
unset TS_AUTHKEY
exec java -jar /app/app.jar
