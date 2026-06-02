ModsSync test kit (Fabric 26.1.2)
=================================

Dummy content to verify ModsSync, with controlled "sides":

  mods/testboth-1.0.0.jar     environment "*"       (both)   -> SHOULD clone to client
  mods/testclient-1.0.0.jar   environment "client"  (client) -> SHOULD clone to client
  mods/testserver-1.0.0.jar   environment "server"  (server) -> SHOULD NOT clone (server-only)
  resourcepacks/TestPackA.zip                                 -> SHOULD clone to client
  resourcepacks/TestPackB.zip                                 -> SHOULD clone to client

SERVER (the 26.1.2 Fabric server you control):
  - In the server's mods/ put:        fabric-api  +  modssync_3.2.1-fabric.26.1.2.jar
                                       + testboth + testclient + testserver  (from this kit)
  - In the server's resourcepacks/ put: TestPackA.zip + TestPackB.zip
  - Start the server.

CLIENT TEST 1 (clean client = fabric-api + modssync only):
  - Join the server.
  - Expected after rejoin/restart:
      mods/ gains:           testboth, testclient        (NOT testserver)
      resourcepacks/ gains:  TestPackA.zip, TestPackB.zip

CLIENT TEST 2 (client also has an extra random mod + an extra resourcepack the server does NOT have):
  - Join the server.
  - Expected:
      the extra MOD        -> disabled (renamed to .jar.disabled)   (mods strict-mirror)
      the extra RESOURCEPACK -> kept (packs are additive, never removed)

Check your client logs/latest.log for lines containing "ModsSync".
