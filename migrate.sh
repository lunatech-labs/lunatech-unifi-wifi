#!/bin/zsh

# Obtain a cookie from your browser and provide them as environment variables, see below.
# Optionally get them from the API directly: $UNIFI_URL/api/login and submit your username and password as JSON.

if [ -z "$ORIGINAL_SITE" ]; then
  echo "ERROR: Set \$ORIGINAL_SITE"
  exit 1
fi

if [ -z "$NEW_SITE" ]; then
  echo "ERROR: Set \$NEW_SITE"
  exit 1
fi

if [ -z "$CSRF_TOKEN" ]; then
  echo "ERROR: Set \$CSRF_TOKEN"
  exit 1
fi

if [ -z "$UNIFI_SES" ]; then
  echo "ERROR: Set \$UNIFI_SES"
  exit 1
fi


UNIFI_URL=${UNIFI_URL:-"https://unifi.lunatech.com:8443"}

echo "Migrating RADIUS users"
echo "Getting all users from ${ORIGINAL_SITE} and migrating them to ${NEW_SITE}"

for account in $(curl -s -H "Cookie: unifises=${UNIFI_SES}; csrf_token=${CSRF_TOKEN}" "${UNIFI_URL}/api/s/${ORIGINAL_SITE}/rest/account" | jq '.data[]' -c); do
  echo "Migrating: $(echo "$account" | jq '.name')"
  new_account="$(echo "$account" | jq '. | {name: .name, x_password: .x_password}')"
  response=$(curl -XPOST -s -H "Cookie: unifises=${UNIFI_SES}; csrf_token=${CSRF_TOKEN}" -H "X-Csrf-Token: ${CSRF_TOKEN}" -H "Content-Type: application/json" "${UNIFI_URL}/api/s/${NEW_SITE}/rest/account" -d "$new_account")
  echo "Done: $(echo "$response" | jq '.meta')"
done

echo "RADIUS account migration done!"
