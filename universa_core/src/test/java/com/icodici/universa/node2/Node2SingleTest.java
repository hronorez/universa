/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/19/17.
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.*;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.DatagramAdapter;
import com.icodici.universa.node2.network.Network;
import net.sergeych.utils.Bytes;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.*;

import java.io.File;
import java.io.FileReader;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.anyOf;
import static org.junit.Assert.*;

public class Node2SingleTest extends BaseNetworkTest {

    private static Network network_s = null;
    private static Node node_s = null;
    private static List<Node> nodes_s = null;
    private static Map<NodeInfo,Node> nodesMap_s = null;
    private static Ledger ledger_s = null;
    private static Config config_s = null;



    @BeforeClass
    public static void beforeClass() throws Exception {
        initTestSet();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        node_s.shutdown();
        node_s.getLedger().close();

        network_s = null;
        node_s = null;
        nodes_s = null;
        nodesMap_s = null;
        ledger_s = null;
        config_s = null;
    }

    private static void initTestSet() throws Exception {
        initTestSet(1, 1);
    }

    private static void initTestSet(int posCons, int negCons) throws Exception {
        config_s = new Config();

        // The quorum bigger than the network capacity: we model the situation
        // when the system will not get the answer
        config_s.setPositiveConsensus(posCons);
        config_s.setNegativeConsensus(negCons);
        config_s.setResyncBreakConsensus(1);
        config_s.setTransactionUnitsIssuerKeyData(Bytes.fromHex("1E 08 1C 01 00 01 C4 00 01 B9 C7 CB 1B BA 3C 30 80 D0 8B 29 54 95 61 41 39 9E C6 BB 15 56 78 B8 72 DC 97 58 9F 83 8E A0 B7 98 9E BB A9 1D 45 A1 6F 27 2F 61 E0 26 78 D4 9D A9 C2 2F 29 CB B6 F7 9F 97 60 F3 03 ED 5C 58 27 27 63 3B D3 32 B5 82 6A FB 54 EA 26 14 E9 17 B6 4C 5D 60 F7 49 FB E3 2F 26 52 16 04 A6 5E 6E 78 D1 78 85 4D CD 7B 71 EB 2B FE 31 39 E9 E0 24 4F 58 3A 1D AE 1B DA 41 CA 8C 42 2B 19 35 4B 11 2E 45 02 AD AA A2 55 45 33 39 A9 FD D1 F3 1F FA FE 54 4C 2E EE F1 75 C9 B4 1A 27 5C E9 C0 42 4D 08 AD 3E A2 88 99 A3 A2 9F 70 9E 93 A3 DF 1C 75 E0 19 AB 1F E0 82 4D FF 24 DA 5D B4 22 A0 3C A7 79 61 41 FD B7 02 5C F9 74 6F 2C FE 9A DD 36 44 98 A2 37 67 15 28 E9 81 AC 40 CE EF 05 AA 9E 36 8F 56 DA 97 10 E4 10 6A 32 46 16 D0 3B 6F EF 80 41 F3 CC DA 14 74 D1 BF 63 AC 28 E0 F1 04 69 63 F7"));
        config_s.getKeysWhiteList().add(config_s.getTransactionUnitsIssuerKey());

        NodeInfo myInfo = new NodeInfo(getNodePublicKey(0), 1, "node1", "localhost",
                7101, 7102, 7104);
        NetConfig nc = new NetConfig(asList(myInfo));
        network_s = new TestSingleNetwork(nc);

        Properties properties = new Properties();

        File file = new File(CONFIG_2_PATH + "config/config.yaml");
        if (file.exists())
            properties.load(new FileReader(file));

        ledger_s = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING, properties);
        node_s = new Node(config_s, myInfo, ledger_s, network_s);
        ((TestSingleNetwork)network_s).addNode(myInfo, node_s);

        nodes_s = new ArrayList<>();
        nodes_s.add(node_s);

        nodesMap_s = new HashMap<>();
        nodesMap_s.put(myInfo, node_s);

//        System.out.println("waiting for sanitation... ");
//        for(Node n : nodes_s) {
//            if(n.isSanitating())
//                n.sanitationFinished.await();
//        }
//        System.out.println("sanitation finished!");
    }



    @Before
    public void setUp() throws Exception {
        init(node_s, nodes_s, nodesMap_s, network_s, ledger_s, config_s);
    }


    @Test
    public void sanitationTest() throws Exception {
        afterClass();

        Thread.sleep(2000);

        List<Contract> origins = new ArrayList<>();
        List<Contract> newRevisions = new ArrayList<>();
        List<Contract> newContracts = new ArrayList<>();
        PrivateKey myKey = TestKeys.privateKey(4);

        final int N = 100;
        for(int i = 0; i < N; i++) {
            Contract origin = new Contract(myKey);
            origin.seal();
            origins.add(origin);

            Contract newRevision = origin.createRevision(myKey);

            if(i < N/2) {
                //ACCEPTED
                newRevision.setOwnerKeys(TestKeys.privateKey(1).getPublicKey());
            } else {
                //DECLINED
                //State is equal
            }

            Contract newContract = new Contract(myKey);
            newRevision.addNewItems(newContract);
            newRevision.seal();

            newContracts.add(newContract);
            newRevisions.add(newRevision);

            System.out.println("item# "+ newRevision.getId().toBase64String().substring(0,6));
            int finalI = i;

            StateRecord originRecord = ledger.findOrCreate(origin.getId());
            originRecord.setExpiresAt(origin.getExpiresAt());
            originRecord.setCreatedAt(origin.getCreatedAt());

            StateRecord newRevisionRecord = ledger.findOrCreate(newRevision.getId());
            newRevisionRecord.setExpiresAt(newRevision.getExpiresAt());
            newRevisionRecord.setCreatedAt(newRevision.getCreatedAt());

            StateRecord newContractRecord = ledger.findOrCreate(newContract.getId());
            newContractRecord.setExpiresAt(newContract.getExpiresAt());
            newContractRecord.setCreatedAt(newContract.getCreatedAt());

            if(new Random().nextBoolean()) {
                if(finalI < N/2) {
                    originRecord.setState(ItemState.REVOKED);
                    newContractRecord.setState(ItemState.APPROVED);
                    newRevisionRecord.setState(ItemState.APPROVED);
                } else {
                    originRecord.setState(ItemState.APPROVED);
                    newContractRecord.setState(ItemState.UNDEFINED);
                    newRevisionRecord.setState(ItemState.DECLINED);
                }
            } else {
                originRecord.setState(ItemState.LOCKED);
                originRecord.setLockedByRecordId(newRevisionRecord.getRecordId());
                newContractRecord.setState(ItemState.LOCKED_FOR_CREATION);
                newContractRecord.setLockedByRecordId(newRevisionRecord.getRecordId());
                newRevisionRecord.setState(finalI < N/2 ? ItemState.PENDING_POSITIVE : ItemState.PENDING_NEGATIVE);
            }

            originRecord.save();
            ledger.putItem(originRecord,origin, Instant.now().plusSeconds(3600*24));
            newRevisionRecord.save();
            ledger.putItem(newRevisionRecord,newRevision, Instant.now().plusSeconds(3600*24));
            if(newContractRecord.getState() == ItemState.UNDEFINED) {
                newContractRecord.destroy();
            } else {
                newContractRecord.save();
            }
        }

        Thread.sleep(2000);

        initTestSet();
        setUp();

        while (node.isSanitating()) {
            System.out.println("Node sanitating " + node.getRecordsToSanitate().size());
            Thread.sleep(2000);
        }
    }

    @Test
    public void noQuorumError() throws Exception {
        afterClass();
        initTestSet(2, 2);
        setUp();

        TestItem item = new TestItem(true);

        System.out.println("noQuorumError " + item.getId());
//        node.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        node.registerItem(item);
        try {
            System.out.println("noQuorumError wait " + item.getId());
            node.waitItem(item.getId(), 5000);
            fail("Expected exception to be thrown.");
        } catch (TimeoutException te) {
            assertNotNull(te);
        }

        @NonNull ItemResult checkedItem = node.checkItem(item.getId());

        assertEquals(ItemState.PENDING_POSITIVE, checkedItem.state);
        assertTrue(checkedItem.expiresAt.isBefore(ZonedDateTime.now().plusHours(5)));

        TestItem item2 = new TestItem(false);

        node.registerItem(item2);
        try {
            node.waitItem(item2.getId(), 5000);
            fail("Expected exception to be thrown.");
        } catch (TimeoutException te) {
            assertNotNull(te);
        }

        checkedItem = node.checkItem(item2.getId());

        afterClass();
        initTestSet(1, 1);

        assertEquals(ItemState.PENDING_NEGATIVE, checkedItem.state);
    }


}