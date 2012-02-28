Introduction
============

Secret codes may be encoded in URIs using the `ocra://` scheme.

`ocra://label?parameters`

Example
=======

`ocra://VPN?user=test&client_secret=48656c6c6f21deadbeef&server_secret=48656c6c6f21deadbeef&url=http://vpn.server/otp`

Parameters
==========

Label
-----

The label can be anything, usually details the service. This can be edited by the user.

User
----

The `user` parameter contains an identifier for this account. This is passed along with every request.

Secrets
-------

Both the `client_secret` and `server_secret` should be hex-encoded secret keys used in the OCRA exchange. The `server_secret` key is used to verify the server's response to the client's challenge. The `client_secret` key is used to generate a response to the server's challenge.

URL
---

The `url` parameter is the link the client will send all its communication to.

Suite
-----

The `suite` parameter is optional, and defines the OCRA suite used. For formatting, see RFC 6287. Defaults to `OCRA-1:HOTP-SHA1-6:QN06`. This parameter is currently ignored by Push Authenticator.