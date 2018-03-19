/*
 * Copyright (c) 2017, All Rights Reserved
 *
 * Written by Leonid Novikov <flint.emerald@gmail.com>
 */

package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.*;
import com.icodici.universa.contract.*;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.DatagramAdapter;
import com.icodici.universa.node2.network.Network;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


@Ignore("It is base test class for network tests, shouldn't be run")
public class BaseNetworkTest extends TestCase {

    protected static final String ROOT_PATH = "./src/test_contracts/";
    protected static final String CONFIG_2_PATH = "./src/test_config_2/";
    protected static Contract tuContract = null;
    protected Network network = null;
    protected Node node = null;
    protected List<Node> nodes = null;
    protected Map<NodeInfo,Node> nodesMap = null;
    protected Ledger ledger = null;
    protected Config config = null;
    protected Object tuContractLock = new Object();



    public void init(Node node, List<Node> nodes, Map<NodeInfo,Node> nodesMap, Network network, Ledger ledger, Config config) throws Exception {
        this.node = node;
        this.nodes = nodes;
        this.nodesMap = nodesMap;
        this.network = network;
        this.ledger = ledger;
        this.config = config;
    }



    @Test(timeout = 900000)
    public void registerGoodItem() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Thread.sleep(500);
        int N = 100;
        for (int k = 0; k < 1; k++) {
//            StopWatch.measure(true, () -> {
            for (int i = 0; i < N; i++) {
                TestItem ok = new TestItem(true);
                System.out.println("--------------register item " + ok.getId() + " ------------");
                node.registerItem(ok);
                for (Node n : nodesMap.values()) {
                    try {
                        ItemResult r = n.waitItem(ok.getId(), 18000);
                        int numIterations = 0;
                        while( !r.state.isConsensusFound()) {
                            System.out.println("wait for consensus receiving on the node " + n + " state is " + r.state);
                            Thread.sleep(500);
                            r = n.waitItem(ok.getId(), 8000);
                            numIterations++;
                            if(numIterations > 20)
                                break;
                        }
                        assertEquals("In node " + n + " item " + ok.getId(), ItemState.APPROVED, r.state);
                    } catch (TimeoutException e) {
                        fail("timeout");
                    }
                }

                ItemResult r = node.waitItem(ok.getId(), 15000);
                assertEquals("after: In node "+node+" item "+ok.getId(), ItemState.APPROVED, r.state);

            }
//            });
        }
    }



    @Test(timeout = 900000)
    public void registerGoodParcel() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Thread.sleep(500);
//        LogPrinter.showDebug(true);
        int N = 100;
        for (int k = 0; k < 1; k++) {
            for (int i = 0; i < N; i++) {

                Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
                stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
                stepaCoins.seal();
                stepaCoins.check();
                stepaCoins.traceErrors();

                Parcel parcel = createParcelWithClassTU(stepaCoins, stepaPrivateKeys);

//                System.out.println("-------------- register parcel " + parcel.getId() + " (iteration " + i + ") ------------");
                node.registerParcel(parcel);
                synchronized (tuContractLock) {
                    tuContract = parcel.getPaymentContract();
                }
                for (Node n : nodesMap.values()) {
                    try {
//                        System.out.println("-------------- wait parcel " + parcel.getId() + " on the node " + n + " (iteration " + i + ") ------------");
                        n.waitParcel(parcel.getId(), 25000);
                        ItemResult r = n.waitItem(parcel.getPayloadContract().getId(), 8000);
                        int numIterations = 0;
                        while( !r.state.isConsensusFound()) {
                            System.out.println("wait for consensus receiving on the node " + n + " state is " + r.state);
                            Thread.sleep(500);
                            n.waitParcel(parcel.getId(), 25000);
                            r = n.waitItem(parcel.getPayloadContract().getId(), 8000);
                            numIterations++;
                            if(numIterations > 20)
                                break;
                        }
                        assertEquals("In node " + n + " parcel " + parcel.getId(), ItemState.APPROVED, r.state);
                    } catch (TimeoutException e) {
//                        System.out.println(n.ping());
////                        System.out.println(n.traceTasksPool());
//                        System.out.println(n.traceParcelProcessors());
//                        System.out.println(n.traceItemProcessors());
                        fail("timeout, node " + n + " parcel " + parcel.getId() + " parcel " + parcel.getId() + " (iteration " + i + ")");
                    }
                }

                node.waitParcel(parcel.getId(), 25000);
                ItemResult r = node.waitItem(parcel.getPayloadContract().getId(), 8000);
                assertEquals("after: In node "+node+" item "+parcel.getId(), ItemState.APPROVED, r.state);
                System.out.println("-------------- parcel " + parcel.getId() + " registered (iteration " + i + ")------------");
//                Thread.sleep(5000);
//                System.out.println("-------------- parcel " + parcel.getId() + " wait finished (iteration " + i + ")------------");

            }
        }
    }



    @Test(timeout = 90000)
    public void registerBadItem() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem bad = new TestItem(false);
        node.registerItem(bad);
        ItemResult r = node.waitItem(bad.getId(), 5000);
        assertEquals(ItemState.DECLINED, r.state);
    }



    @Test(timeout = 90000)
    public void checkItem() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem ok = new TestItem(true);
        TestItem bad = new TestItem(false);
        node.registerItem(ok);
        node.registerItem(bad);
        node.waitItem(ok.getId(), 6000);
        node.waitItem(bad.getId(), 6000);
        assertEquals(ItemState.APPROVED, node.checkItem(ok.getId()).state);
        assertEquals(ItemState.DECLINED, node.checkItem(bad.getId()).state);
    }



    @Test(timeout = 90000)
    public void shouldCreateItems() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem item = new TestItem(true);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 16000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.APPROVED, result.state);
    }



    @Test(timeout = 90000)
    public void shouldDeclineItems() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem item = new TestItem(false);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.DECLINED, result.state);
        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.DECLINED, result.state);
    }



    @Test(timeout = 90000)
    public void singleNodeMixApprovedAndDeclined() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem item = new TestItem(true);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.APPROVED, result.state);


        // Negative consensus
        TestItem item2 = new TestItem(false);

        node.registerItem(item2);
        ItemResult result2 = node.waitItem(item2.getId(), 12000);
        assertEquals(ItemState.DECLINED, result2.state);

        result2 = node.waitItem(item2.getId(), 12000);
        assertEquals(ItemState.DECLINED, result2.state);
        result2 = node.waitItem(item2.getId(), 12000);
        assertEquals(ItemState.DECLINED, result2.state);

        result2 = node.checkItem(item2.getId());
        assertEquals(ItemState.DECLINED, result2.state);
    }



    @Test(timeout = 90000)
    public void timeoutError() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Duration savedMaxElectionsTime = config.getMaxElectionsTime();

        config.setMaxElectionsTime(Duration.ofMillis(200));

        TestItem item = new TestItem(true);

        // We start elections but no node in the network know the source, so it
        // will short-circuit to self and then stop by the timeout:

        ItemResult itemResult = node.checkItem(item.getId());
        assertEquals(ItemState.UNDEFINED, itemResult.state);
        assertFalse(itemResult.haveCopy);
        assertNull(itemResult.createdAt);
        assertNull(itemResult.expiresAt);

        itemResult = node.waitItem(item.getId(), 100);
        assertEquals(ItemState.UNDEFINED, itemResult.state);

        itemResult = node.checkItem(item.getId());
        assertEquals(ItemState.UNDEFINED, itemResult.state);

        config.setMaxElectionsTime(savedMaxElectionsTime);
    }



    @Test(timeout = 90000)
    public void testNotCreatingOnReject() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(false);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 12500);

        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertEquals(ItemState.UNDEFINED, itemNew2.state);
    }



    @Test(timeout = 90000)
    public void rejectBadNewItem() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(false);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        System.out.println("-------------- register item " + main.getId() + " --------");

        node.registerItem(main);
        System.out.println("-------------- wait item " + main.getId() + " --------");
        ItemResult itemResult = node.waitItem(main.getId(), 15000);

        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertEquals(ItemState.UNDEFINED, itemNew2.state);
    }



    @Test(timeout = 90000)
    public void badNewDocumentsPreventAccepting() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        // and now we run the day for teh output document:
        node.registerItem(new2);
        node.waitItem(new2.getId(), 3000);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        @NonNull ItemResult item = node.checkItem(main.getId());
        assertEquals(ItemState.UNDEFINED, item.state);

        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 12000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.waitItem(new1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        // and this one was created before
        @NonNull ItemResult itemNew2 = node.waitItem(new2.getId(), 2000);
        assertEquals(ItemState.APPROVED, itemNew2.state);

        LogPrinter.showDebug(false);
    }



    @Test(timeout = 90000)
    public void acceptWithReferences() throws Exception {
        return;
    }



    @Test(timeout = 35000)
    public void badReferencesDeclineListStates() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {

            Thread.sleep(300);

            System.out.println("-------------- check bad state " + badState + " isConsensusFind(" + badState.isConsensusFound() + ") --------");

            TestItem main = new TestItem(true);

            TestItem existingItem1 = new TestItem(true);
            StateRecord existing1 = ledger.findOrCreate(existingItem1.getId());
            existing1.setState(ItemState.APPROVED).save();


            // but second is not good
            TestItem existingItem2 = new TestItem(false);
            StateRecord existing2 = ledger.findOrCreate(existingItem2.getId());
            existing2.setState(badState).save();

            main.addReferencedItems(existingItem1, existingItem2);

            Thread.sleep(300);

            // check that main is fully approved
            node.registerItem(main);
            ItemResult itemResult = node.waitItem(main.getId(), 10000);
            assertEquals(ItemState.DECLINED, itemResult.state);

            // and the references are intact
            while(ItemState.APPROVED != existing1.getState()) {
                Thread.sleep(500);
                System.out.println(existing1.reload().getState());
            }
            assertEquals(ItemState.APPROVED, existing1.getState());

            while (badState != existing2.getState()) {
                Thread.sleep(500);
                System.out.println(existing2.reload().getState());
            }
            assertEquals(badState, existing2.getState());
        }
    }



    @Test(timeout = 90000)
    public void badReferencesDecline() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem existing1 = new TestItem(false);
        TestItem existing2 = new TestItem(true);

        System.out.println("--------resister (bad) item " + existing1.getId() + " ---------");
        node.registerItem(existing1);
        ItemResult ir = node.waitItem(existing1.getId(), 6000);

        System.out.println("--------resister (good) item " + existing2.getId() + " ---------");
        node.registerItem(existing2);
        node.waitItem(existing2.getId(), 6000);

        main.addReferencedItems(existing1, existing2);
        main.addNewItems(new1, new2);

        System.out.println("--------resister (main) item " + main.getId() + " ---------");

        // check that main is fully approved
        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 15000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        assertEquals(ItemState.UNDEFINED, node.waitItem(new1.getId(), 3000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(new2.getId(), 3000).state);

        // and the references are intact
        assertEquals(ItemState.DECLINED, node.waitItem(existing1.getId(), 3000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(existing2.getId(), 3000).state);
    }



    @Test(timeout = 90000)
    public void missingReferencesDecline() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        TestItem main = new TestItem(true);

        TestItem existing = new TestItem(true);
        node.registerItem(existing);
        @NonNull ItemResult existingItem = node.waitItem(existing.getId(), 15000);

        // but second is missing
        TestItem missing = new TestItem(true);

        main.addReferencedItems(existing, missing);

        // check that main is declined
        System.out.println("--------- missind id: " + missing.getId());
        System.out.println("--------- existing id: " + existing.getId());
        node.registerItem(main);
        // need some time to resync missingId
        ItemResult itemResult = node.waitItem(main.getId(), 15000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // and the references are intact
        assertEquals(ItemState.APPROVED, existingItem.state);

        System.out.println(node.getItem(missing.getId()));

        assertNull(node.getItem(missing.getId()));
    }



    // this test can't be executed in a network as it needs setup in all 3 ledgers
//    @Test
//    public void approveAndRevoke() throws Exception {
//        TestItem main = new TestItem(true);
//
//        StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
//        existing1.setState(ItemState.APPROVED).save();
//        StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
//        existing2.setState(ItemState.APPROVED).save();
//
//        main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));
//
//        // check that main is fully approved
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 100);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//
//        // and the references are intact
//        assertEquals(ItemState.REVOKED, node.checkItem(existing1.getId()).state);
//        assertEquals(ItemState.REVOKED, node.checkItem(existing2.getId()).state);
//    }
//



    @Test(timeout = 60000)
    public void badRevokingItemsDeclineAndRemoveLock() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {


            System.out.println("-------------- check bad state " + badState + " isConsensusFind(" + badState.isConsensusFound() + ") --------");

            Thread.sleep(300);
            TestItem main = new TestItem(true);
            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
            existing1.setState(ItemState.APPROVED).save();
            // but second is not good
            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
            existing2.setState(badState).save();

            main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));

            Thread.sleep(300);

            node.registerItem(main);
            ItemResult itemResult = node.waitItem(main.getId(), 15000);
            assertEquals(ItemState.DECLINED, itemResult.state);

            // and the references are intact
            while (ItemState.APPROVED != existing1.getState()) {
                Thread.sleep(500);
                System.out.println(existing1.reload().getState());
            }
            assertEquals(ItemState.APPROVED, existing1.getState());

            while (badState != existing2.getState()) {
                Thread.sleep(500);
                System.out.println(existing2.reload().getState());
            }
            assertEquals(badState, existing2.getState());
        }
    }

    @Test(timeout = 90000)
    public void registerDeepTree() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem new1_1 = new TestItem(true);
        TestItem new2_1 = new TestItem(true);

        new1.addNewItems(new1_1);
        new2.addNewItems(new2_1);
        main.addNewItems(new1, new2);

        node.registerItem(main);
        ItemResult result = node.waitItem(main.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(new1.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(new2.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(new1_1.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(new2_1.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);
    }

    @Test(timeout = 90000)
    public void registerDeepTreeWithRevoke() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem new1_1 = new TestItem(true);
        TestItem new2_1 = new TestItem(true);
        TestItem revoke1 = new TestItem(true);
        TestItem revoke2 = new TestItem(true);

        node.registerItem(revoke1);
        assertEquals(ItemState.APPROVED, node.waitItem(revoke1.getId(), 15000).state);

        node.registerItem(revoke2);
        assertEquals(ItemState.APPROVED, node.waitItem(revoke2.getId(), 15000).state);

        new1_1.addRevokingItems(revoke1);
        new2_1.addRevokingItems(revoke2);
        new1.addNewItems(new1_1);
        new2.addNewItems(new2_1);
        main.addNewItems(new1, new2);

        node.registerItem(main);
        ItemResult result = node.waitItem(main.getId(), 15000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(new1.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(new2.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(new1_1.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(new2_1.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(revoke1.getId(), 2000);
        assertEquals(ItemState.REVOKED, result.state);
        result = node.waitItem(revoke2.getId(), 2000);
        assertEquals(ItemState.REVOKED, result.state);
    }

    @Test(timeout = 90000)
    public void declineDeepTreeBadNew() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem new1_1 = new TestItem(true);
        TestItem new2_1 = new TestItem(false);

        new1.addNewItems(new1_1);
        new2.addNewItems(new2_1);
        main.addNewItems(new1, new2);

        node.registerItem(main);
        ItemResult result = node.waitItem(main.getId(), 6000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(new1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(new2.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);

        result = node.waitItem(new1_1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(new2_1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
    }

    @Test(timeout = 90000)
    public void declineDeepTreeBadRevoke() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem revoke1 = new TestItem(true);


        TestItem new1_1 = new TestItem(true);
        TestItem revoke1_1 = new TestItem(true);

        new1_1.addRevokingItems(revoke1_1);
        new1.addNewItems(new1_1);
        new1.addRevokingItems(revoke1);
        main.addNewItems(new1);

        node.registerItem(main);
        ItemResult result = node.waitItem(main.getId(), 6000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(new1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(revoke1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);

        result = node.waitItem(new1_1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(revoke1_1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
    }


    @Test(timeout = 90000)
    public void createRealContract() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Contract c = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        c.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();

//        LogPrinter.showDebug(true);
        registerAndCheckApproved(c);
    }

    @Test
    public void createNotaryContract() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Contract c = Contract.fromDslFile(ROOT_PATH + "notary_4096.yaml");
        c.addSignerKeyFromFile(ROOT_PATH + "keys/romanuskov_4096.private.unikey");
        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.isOk());

        registerAndCheckApproved(c);
    }

    @Test(timeout = 90000)
    public void checkSimpleCase() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

//        String transactionName = "./src/test_contracts/transaction/93441e20-242a-4e91-b283-8d0fd5f624dd.transaction";

        for (int i = 0; i < 5; i++) {
            Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
            contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
            contract.seal();

            addDetailsToAllLedgers(contract);

            contract.check();
            contract.traceErrors();
            assertTrue(contract.isOk());

            Parcel parcel = registerWithNewParcel(contract);
            node.waitParcel(parcel.getId(), 8000);
            ItemResult itemResult = node.waitItem(parcel.getPayloadContract().getId(), 3000);
            if (ItemState.APPROVED != itemResult.state)
                fail("Wrong state on repetition " + i + ": " + itemResult + ", " + itemResult.errors +
                        " \r\ncontract_errors: " + contract.getErrors());

            assertEquals(ItemState.APPROVED, itemResult.state);
        }
    }


    // split and join section

    @Test(timeout = 90000)
    public void shouldApproveSplit() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKey(key);
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);

        // 100 - 30 = 70
        Contract c1 = ContractsService.createSplit(c, 30, "amount", new HashSet<PrivateKey>(Arrays.asList(key)));
        Contract c2 = c1.getNew().get(0);
        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        registerAndCheckApproved(c1);
        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        assertEquals(ItemState.REVOKED, node.waitItem(c.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(c1.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(c2.getId(), 5000).state);
    }

    @Test(timeout = 90000)
    public void shouldDeclineSplit() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKey(key);
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);

        // 550
        Contract c1 = ContractsService.createSplit(c, 550, "amount", new HashSet<PrivateKey>(Arrays.asList(key)));
        Contract c2 = c1.getNew().get(0);
        assertEquals("-450", c1.getStateData().get("amount").toString());
        assertEquals("550", c2.getStateData().get("amount").toString());

        registerAndCheckDeclined(c1);

        assertEquals(ItemState.APPROVED, node.waitItem(c.getId(), 5000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(c1.getId(), 5000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(c2.getId(), 5000).state);
    }

    @Test(timeout = 90000)
    public void shouldApproveSplitAndJoinWithNewSend() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> keys = new HashSet<>();
        keys.add(key);
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKey(key);
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);
        assertEquals(100, c.getStateData().get("amount"));

        // split 100 - 30 = 70
        Contract c1 = ContractsService.createSplit(c, 30, "amount", new HashSet<PrivateKey>(Arrays.asList(key)));
        Contract c2 = c1.getNew().get(0);
        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        registerAndCheckApproved(c1);
        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        assertEquals(ItemState.REVOKED, node.waitItem(c.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(c1.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(c2.getId(), 5000).state);


        // join 70 + 30 = 100
        Contract c3 = ContractsService.createJoin(c1, c2, "amount", keys);
        c3.check();
        c3.traceErrors();
        assertTrue(c3.isOk());

        registerAndCheckApproved(c3);
        assertEquals(new Decimal(100), c3.getStateData().get("amount"));

        assertEquals(ItemState.REVOKED, node.waitItem(c.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(c1.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(c2.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(c3.getId(), 5000).state);
    }

    @Test(timeout = 90000)
    public void shouldDeclineSplitAndJoinWithWrongAmount() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKey(key);
        assertTrue(c.check());
        c.seal();

        registerAndCheckApproved(c);
        assertEquals(100, c.getStateData().get("amount"));

        // split 100 - 30 = 70
        Contract c1 = ContractsService.createSplit(c, 30, "amount", new HashSet<PrivateKey>(Arrays.asList(key)));
        Contract c2 = c1.getNew().get(0);
        registerAndCheckApproved(c1);
        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());


        //wrong. send 500 out of 2 contracts (70 + 30)
        Contract c3 = c2.createRevision();
        c3.getStateData().set("amount", new Decimal(500));
        c3.addSignerKey(key);
        c3.addRevokingItems(c1);
        assertFalse(c3.check());
        c3.seal();

        registerAndCheckDeclined(c3);
    }

//    @Test
//    public void checkSergeychCase() throws Exception {
//    String transactionName = "./src/test_contracts/transaction/e00b7488-9a8f-461f-96f6-177c6272efa0.transaction";
//
//        for( int i=0; i < 5; i++) {
//            Contract contract = readContract(transactionName, true);
//
//            HashId id;
//            StateRecord record;
//
//            for (Approvable c : contract.getRevokingItems()) {
//                id = c.getId();
//                record = ledger.findOrCreate(id);
//                record.setState(ItemState.APPROVED).save();
//            }
//
//            for( Approvable c: contract.getNewItems()) {
//                record = ledger.getRecord(c.getId());
//                if( record != null )
//                    record.destroy();
//            }
//
//            StateRecord r = ledger.getRecord(contract.getId());
//            if( r !=  null ) {
//                r.destroy();
//            }
//
//            contract.check();
//            contract.traceErrors();
//            assertTrue(contract.isOk());
//
//            @NonNull ItemResult ir = node.registerItem(contract);
////            System.out.println("-- "+ir);
//            ItemResult itemResult = node.waitItem(contract.getId(), 15000);
//            if( ItemState.APPROVED != itemResult.state)
//                fail("Wrong state on repetition "+i+": "+itemResult+", "+itemResult.errors);
//            assertEquals(ItemState.APPROVED, itemResult.state);
//        }
//    }



    // quantizer section

    @Test(timeout = 90000)
    public void shouldBreakByQuantizer() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        // 100
        Contract.setTestQuantaLimit(10);
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        c.seal();

        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 1500);
        System.out.println(itemResult);
        Contract.setTestQuantaLimit(-1);

        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }

    @Test(timeout = 90000)
    public void shouldBreakByQuantizerSplit() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        c.seal();

        registerAndCheckApproved(c);


        Contract.setTestQuantaLimit(60);
        // 30
        Contract c1 = ContractsService.createSplit(c, 30, "amount", new HashSet<PrivateKey>(Arrays.asList(key)));
        Contract c2 = c1.getNew().get(0);

        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        node.registerItem(c1);
        ItemResult itemResult = node.waitItem(c1.getId(), 1500);
        System.out.println(itemResult);
        Contract.setTestQuantaLimit(-1);

        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }


    @Test(timeout = 90000)
    public void shouldBreakByQuantizerDeepTree() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        Contract swapContract;

        // swap contract has two contracts in new items, and each of them has one in own revoking
        // so we have contracts tree with 3 levels
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());

        // Check 4096 bits signature container contract (8) +
        // register version container contract (20) +

        // Check 2048 bits marty signature swap contract (1) +
        // register version swap contract (20) +

        // Check 2048 bits marty signature new delorean (1) +
        // Check 2048 bits stepa signature new delorean (1) +
        // register version new delorean contract (20) +
        // Check 4096 bits signature revoking old delorean (8) +
        // revoke version old delorean contract (20) +
        // Check reference new delorean (1) +
        // Check owner permission new delorean (1) +
        // Check revoke permission old delorean (1) +

        // Check 2048 bits stepa signature new lamborghini (1) +
        // Check 2048 bits marty signature new lamborghini (1) +
        // register version new lamborghini contract (20) +
        // Check 4096 bits signature revoking old lamborghini (8) +
        // revoke version old lamborghini contract (20) +
        // Check reference new lamborghini (1) +
        // Check owner permission new lamborghini (1) +
        // Check revoke permission old lamborghini (1) +
        Contract.setTestQuantaLimit(154);
        swapContract = imitateSendingTransactionToPartner(swapContract);
        Contract container = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        container.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        container.addNewItems(swapContract);
        container.seal();
        node.registerItem(container);

        ItemResult result = node.waitItem(container.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);

        result = node.waitItem(swapContract.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);

        Contract.setTestQuantaLimit(-1);
    }


    // swap section

    public void prepareContractsForSwap(
            Set<PrivateKey> martyPrivateKeys,
            Set<PublicKey> martyPublicKeys,
            Set<PrivateKey> stepaPrivateKeys,
            Set<PublicKey> stepaPublicKeys,
            Contract delorean,
            Contract lamborghini) throws Exception {

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : martyPrivateKeys) {
            martyPublicKeys.add(pk.getPublicKey());
        }

        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
        delorean.traceErrors();
        registerAndCheckApproved(delorean);
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().isAllowedForKeys(martyPublicKeys));

        lamborghini.addSignerKey(manufacturePrivateKey);
        lamborghini.seal();
        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
        lamborghini.traceErrors();
        registerAndCheckApproved(lamborghini);
        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().isAllowedForKeys(stepaPublicKeys));
    }


    public void checkSwapResultSuccess(Contract swapContract,
                                       Contract delorean,
                                       Contract lamborghini,
                                       Set<PublicKey> martyPublicKeys,
                                       Set<PublicKey> stepaPublicKeys ) throws TimeoutException, InterruptedException {
        // check old revisions for ownership contracts
        System.out.println("--- check old revisions for ownership contracts ---");

        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().isAllowedForKeys(martyPublicKeys));
        assertEquals(ItemState.REVOKED, deloreanResult.state);

        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertEquals(ItemState.REVOKED, lamborghiniResult.state);

        // check new revisions for ownership contracts
        System.out.println("--- check new revisions for ownership contracts ---");

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }

        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertEquals(ItemState.APPROVED, deloreanResult.state);
        assertTrue(newDelorean.getOwner().isAllowedForKeys(stepaPublicKeys));

        lamborghiniResult = node.waitItem(newLamborghini.getId(), 5000);
        System.out.println("Lamborghini ownership contract revision " + newLamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborghini.getOwner().isAllowedForKeys(martyPublicKeys));
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
        assertTrue(newLamborghini.getOwner().isAllowedForKeys(martyPublicKeys));
    }


    public void checkSwapResultDeclined(Contract swapContract,
                                        Contract delorean,
                                        Contract lamborghini,
                                        Set<PublicKey> martyPublicKeys,
                                        Set<PublicKey> stepaPublicKeys ) throws TimeoutException, InterruptedException {
        // check old revisions for ownership contracts
        System.out.println("--- check old revisions for ownership contracts ---");

        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean revoked ownership still belongs to Marty: " + delorean.getOwner().isAllowedForKeys(martyPublicKeys));
        assertEquals(ItemState.APPROVED, deloreanResult.state);

        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini revoked ownership still belongs to Stepa: " + lamborghini.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);

        // check new revisions for ownership contracts
        System.out.println("--- check new revisions for ownership contracts ---");

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }

        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean ownership should belongs to Stepa: " + newDelorean.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
        assertTrue(newDelorean.getOwner().isAllowedForKeys(stepaPublicKeys));

        lamborghiniResult = node.waitItem(newLamborghini.getId(), 5000);
        System.out.println("Lamborghini ownership contract revision " + newLamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini ownership should belongs to Marty: " + newLamborghini.getOwner().isAllowedForKeys(martyPublicKeys));
        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
        assertTrue(newLamborghini.getOwner().isAllowedForKeys(martyPublicKeys));
    }

    private void checkCreateTwoSignedContractAllGood(boolean newRev) throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        Contract baseContract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();

        System.out.println("Base contract contract is valid: " + baseContract.isOk());

        if (newRev)
            registerAndCheckApproved(baseContract);

        Contract twoSignContract = ContractsService.createTwoSignedContract(baseContract, martyPrivateKeys, stepaPublicKeys, newRev);

        twoSignContract = imitateSendingTransactionToPartner(twoSignContract);

        twoSignContract.addSignatureToSeal(stepaPrivateKeys);

        twoSignContract.check();
        twoSignContract.traceErrors();
        registerAndCheckDeclined(twoSignContract);

        twoSignContract = imitateSendingTransactionToPartner(twoSignContract);

        twoSignContract.addSignatureToSeal(martyPrivateKeys);

        twoSignContract.check();
        twoSignContract.traceErrors();
        System.out.println("Contract with two signature is valid: " + twoSignContract.isOk());
        registerAndCheckApproved(twoSignContract);
    }

    @Test(timeout = 90000)
    public void createTwoSignedContractAllGood() throws Exception {
        checkCreateTwoSignedContractAllGood(false);
        checkCreateTwoSignedContractAllGood(true);
    }

    @Test(timeout = 90000)
    public void createTokenContractAllGood() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        Contract tokenContract = ContractsService.createTokenContract(martyPrivateKeys,stepaPublicKeys,"100000000000");

        tokenContract.check();
        tokenContract.traceErrors();
        registerAndCheckApproved(tokenContract);
    }

    @Test(timeout = 90000)
    public void createShareContractAllGood() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        Contract shareContract = ContractsService.createShareContract(martyPrivateKeys,stepaPublicKeys,"100");

        shareContract.check();
        shareContract.traceErrors();
        registerAndCheckApproved(shareContract);

    }

    @Test(timeout = 90000)
    public void createNotaryContractAllGood() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        Contract notaryContract = ContractsService.createNotaryContract(martyPrivateKeys,stepaPublicKeys);

        notaryContract.check();
        notaryContract.traceErrors();
        registerAndCheckApproved(notaryContract);

    }


    @Test(timeout = 90000)
    public void changeOwnerWithAnonId() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        c1.seal();
        c1.check();
        c1.traceErrors();
        registerAndCheckApproved(c1);

        //

        AnonymousId stepaAnonId = AnonymousId.fromBytes(stepaPublicKeys.iterator().next().createAnonymousId());
        Contract anonOwnerContract = c1.createRevisionAnonymously(Arrays.asList(key));
        anonOwnerContract.addSignerKey(key);
        anonOwnerContract.setOwnerKey(stepaAnonId);
        anonOwnerContract.seal();
        anonOwnerContract.check();
        anonOwnerContract.traceErrors();
        registerAndCheckApproved(anonOwnerContract);

        assertTrue(anonOwnerContract.getOwner().getAnonymousIds().iterator().next().equals(stepaAnonId));
        assertEquals(0, anonOwnerContract.getOwner().getKeys().size());

        //

        Contract anonSignedContract = anonOwnerContract.createRevisionAnonymously(stepaPrivateKeys);
        anonSignedContract.addSignerKeys(stepaPrivateKeys);
        anonSignedContract.setOwnerKeys(martyPublicKeys);
        anonSignedContract.seal();
        anonSignedContract.check();
        anonSignedContract.traceErrors();

        Contract afterSend = imitateSendingTransactionToPartner(anonSignedContract);

        registerAndCheckApproved(afterSend);

        assertEquals(0, afterSend.getOwner().getAnonymousIds().size());
        assertTrue(afterSend.getOwner().isAllowedForKeys(martyPublicKeys));

        Contract anonPublishedContract = new Contract(anonSignedContract.getLastSealedBinary());
        ItemResult itemResult = node.waitItem(anonPublishedContract.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
        assertFalse(anonPublishedContract.getSealedByKeys().contains(stepaPublicKeys.iterator().next()));
    }

    @Test(timeout = 90000)
    public void changeOwnerWithAnonId2() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        c1.seal();
        c1.check();
        c1.traceErrors();
        registerAndCheckApproved(c1);

        //

        AnonymousId stepaAnonId = AnonymousId.fromBytes(stepaPublicKeys.iterator().next().createAnonymousId());
        Contract anonOwnerContract = c1.createRevision(key);
        anonOwnerContract.setOwnerKey(stepaAnonId);
        anonOwnerContract.seal();
        anonOwnerContract.check();
        anonOwnerContract.traceErrors();
        registerAndCheckApproved(anonOwnerContract);

        assertTrue(anonOwnerContract.getOwner().getAnonymousIds().iterator().next().equals(stepaAnonId));
        assertEquals(0, anonOwnerContract.getOwner().getKeys().size());

        //

        Contract anonSignedContract = anonOwnerContract.createRevision();
        anonSignedContract.setOwnerKeys(martyPublicKeys);
        anonSignedContract.setCreatorKeys(stepaAnonId);
        anonSignedContract.addSignerKey(stepaPrivateKeys.iterator().next());
        anonSignedContract.seal();
        anonSignedContract.check();
        anonSignedContract.traceErrors();

        Contract afterSend = imitateSendingTransactionToPartner(anonSignedContract);

        registerAndCheckApproved(afterSend);

        assertEquals(0, afterSend.getOwner().getAnonymousIds().size());
        assertTrue(afterSend.getOwner().isAllowedForKeys(martyPublicKeys));

        Contract anonPublishedContract = new Contract(anonSignedContract.getLastSealedBinary());
        ItemResult itemResult = node.waitItem(anonPublishedContract.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
        assertFalse(anonPublishedContract.getSealedByKeys().contains(stepaPublicKeys.iterator().next()));
    }

    @Test(timeout = 90000)
    public void changeOwnerWithAddress() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        c1.seal();
        c1.check();
        c1.traceErrors();
        registerAndCheckApproved(c1);

        //

        KeyAddress stepaAddress = stepaPublicKeys.iterator().next().getShortAddress();
        Contract anonOwnerContract = c1.createRevisionWithAddress(Arrays.asList(key));
        anonOwnerContract.addSignerKey(key);
        anonOwnerContract.setOwnerKey(stepaAddress);
        anonOwnerContract.seal();
        anonOwnerContract.check();
        anonOwnerContract.traceErrors();

        Contract anonAfterSend = imitateSendingTransactionToPartner(anonOwnerContract);

        registerAndCheckApproved(anonAfterSend);

        assertTrue(anonAfterSend.getOwner().getKeyAddresses().iterator().next().equals(stepaAddress));
        assertEquals(0, anonAfterSend.getOwner().getKeys().size());

        //

        Contract anonSignedContract = anonAfterSend.createRevisionWithAddress(stepaPrivateKeys);
        anonSignedContract.addSignerKeys(stepaPrivateKeys);
        anonSignedContract.setOwnerKeys(martyPublicKeys);
        anonSignedContract.seal();
        anonSignedContract.check();
        anonSignedContract.traceErrors();

        Contract afterSend = imitateSendingTransactionToPartner(anonSignedContract);

        registerAndCheckApproved(afterSend);

        assertEquals(0, afterSend.getOwner().getKeyAddresses().size());
        assertTrue(afterSend.getOwner().isAllowedForKeys(martyPublicKeys));

        Contract anonPublishedContract = new Contract(anonSignedContract.getLastSealedBinary());
        ItemResult itemResult = node.waitItem(anonPublishedContract.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
        assertFalse(anonPublishedContract.getSealedByKeys().contains(stepaPublicKeys.iterator().next()));
    }

    @Test(timeout = 90000)
    public void changeOwnerWithAddress2() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        c1.seal();
        c1.check();
        c1.traceErrors();
        registerAndCheckApproved(c1);

        //

        KeyAddress stepaAddress = stepaPublicKeys.iterator().next().getLongAddress();
        Contract anonOwnerContract = c1.createRevision(key);
        anonOwnerContract.setOwnerKey(stepaAddress);
        anonOwnerContract.seal();
        anonOwnerContract.check();
        anonOwnerContract.traceErrors();
        registerAndCheckApproved(anonOwnerContract);

        assertTrue(anonOwnerContract.getOwner().getKeyAddresses().iterator().next().equals(stepaAddress));
        assertEquals(0, anonOwnerContract.getOwner().getKeys().size());

        //

        Contract anonSignedContract = anonOwnerContract.createRevision();
        anonSignedContract.setOwnerKeys(martyPublicKeys);
        anonSignedContract.setCreatorKeys(stepaAddress);
        anonSignedContract.addSignerKey(stepaPrivateKeys.iterator().next());
        anonSignedContract.seal();
        anonSignedContract.check();
        anonSignedContract.traceErrors();

        Contract afterSend = imitateSendingTransactionToPartner(anonSignedContract);

        registerAndCheckApproved(afterSend);

        assertEquals(0, afterSend.getOwner().getKeyAddresses().size());
        assertTrue(afterSend.getOwner().isAllowedForKeys(martyPublicKeys));

        Contract anonPublishedContract = new Contract(anonSignedContract.getLastSealedBinary());
        ItemResult itemResult = node.waitItem(anonPublishedContract.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
        assertFalse(anonPublishedContract.getSealedByKeys().contains(stepaPublicKeys.iterator().next()));
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionAllGood() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckApproved(swapContract);

        checkSwapResultSuccess(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapManyContractsViaTransactionAllGood() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean1 = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract delorean2 = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract delorean3 = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        List<Contract> deloreans = new ArrayList<>();
        deloreans.add(delorean1);
        deloreans.add(delorean2);
        deloreans.add(delorean3);
        Contract lamborghini1 = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        Contract lamborghini2 = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        List<Contract> lamborghinis = new ArrayList<>();
        lamborghinis.add(lamborghini1);
        lamborghinis.add(lamborghini2);

        // ----- prepare contracts -----------

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : martyPrivateKeys) {
            martyPublicKeys.add(pk.getPublicKey());
        }

        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        for(Contract d : deloreans) {
            d.addSignerKey(manufacturePrivateKey);
            d.seal();
            registerAndCheckApproved(d);
        }

        for(Contract l : lamborghinis) {
            l.addSignerKey(manufacturePrivateKey);
            l.seal();
            registerAndCheckApproved(l);
        }

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(deloreans, lamborghinis, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk() + " num new contracts: " + swapContract.getNewItems().size());
        registerAndCheckApproved(swapContract);

        for(Contract d : deloreans) {
            assertEquals(ItemState.REVOKED, node.waitItem(d.getId(), 5000).state);
            System.out.println("delorean is " + node.waitItem(d.getId(), 5000).state);
        }
        for(Contract l : lamborghinis) {
            assertEquals(ItemState.REVOKED, node.waitItem(l.getId(), 5000).state);
            System.out.println("lamborghini is " + node.waitItem(l.getId(), 5000).state);
        }

        for(Approvable a : swapContract.getNewItems()) {
            assertEquals(ItemState.APPROVED, node.waitItem(a.getId(), 5000).state);
            System.out.println("new is " + node.waitItem(a.getId(), 5000).state);
        }

//        checkSwapResultSuccess(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionOneNotSign1() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        // erase one sign!
        Contract newDelorean = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
        }
        newDelorean.getSealedByKeys().removeAll(martyPublicKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionOneNotSign2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        // erase one sign
        Contract newDelorean = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
        }
        newDelorean.getSealedByKeys().removeAll(stepaPublicKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionOneNotSign3() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        // erase one sign
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        newLamborghini.removeAllSignatures();
        newLamborghini.addSignatureToSeal(stepaPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionOneNotSign4() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        // erase one sign
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        newLamborghini.removeAllSignatures();
        newLamborghini.addSignatureToSeal(martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionOneWrongSign1() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
//        swapContract = imitateSendingTransactionToPartner(swapContract);
//        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        // WRONG SIGN!
        PrivateKey wrongSign = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        finishSwap_wrongKey(swapContract, martyPrivateKeys, wrongSign);
        //

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionOneWrongSign2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
//        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);
        // WRONG SIGN!
        PrivateKey wrongSign = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        signPresentedSwap_wrongKey(swapContract, stepaPrivateKeys, wrongSign);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionOneWrongSign3() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);
        // WRONG SIGN!
        PrivateKey wrongSign = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        swapContract = startSwap_wrongKey(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys, wrongSign);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }



    @Test(timeout = 90000)
    public void swapContractsViaTransactionWrongTID1() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        Contract newDelorean = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
        }
        newDelorean.getTransactional().setId(HashId.createRandom().toBase64String());
        newDelorean.seal();
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);



        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionWrongTID2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);

        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        newLamborghini.getTransactional().setId(HashId.createRandom().toBase64String());

        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionMissingTransactional1() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        System.out.println(newDelorean.getTransactional().getId());
        System.out.println(newLamborghini.getTransactional().getId());
        System.out.println(newDelorean.getTransactional().getReferences().get(0).transactional_id);
        System.out.println(newLamborghini.getTransactional().getReferences().get(0).transactional_id);

        // erase both of transactional_id
        newDelorean.getTransactional().setId("");
        newLamborghini.getTransactional().setId("");
        newDelorean.getTransactional().getReferences().get(0).transactional_id = "";
        newLamborghini.getTransactional().getReferences().get(0).transactional_id = "";

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionMissingTransactional2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        System.out.println(newDelorean.getTransactional().getId());
        System.out.println(newLamborghini.getTransactional().getId());
        System.out.println(newDelorean.getTransactional().getReferences().get(0).transactional_id);
        System.out.println(newLamborghini.getTransactional().getReferences().get(0).transactional_id);

        // set both of transactional_id to null
        newDelorean.getTransactional().setId(null);
        newLamborghini.getTransactional().setId(null);
        newDelorean.getTransactional().getReferences().get(0).transactional_id = null;
        newLamborghini.getTransactional().getReferences().get(0).transactional_id = null;

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 60000)
    public void swapContractsViaTransactionWrongCID() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // set wrong reference.contract_id for second contract
        int iHack = 1;
        if(swapContract.getNew().get(0).getParent().equals(lamborghini.getId()))
            iHack = 0;
        swapContract.getNew().get(iHack).getTransactional().getReferences().get(0).contract_id = HashId.createRandom();
        swapContract.getNew().get(iHack).seal();
        swapContract.getNew().get(iHack).addSignatureToSeal(stepaPrivateKeys);
        swapContract.seal();

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        System.out.println(newDelorean.getTransactional().getId());
        System.out.println(newLamborghini.getTransactional().getId());
        System.out.println(newDelorean.getReferences().values().iterator().next().transactional_id);
        System.out.println(newLamborghini.getReferences().values().iterator().next().transactional_id);

        System.out.println(newDelorean.getTransactional().getReferences().get(0));
        System.out.println(newLamborghini.getTransactional().getReferences().get(0));
        System.out.println(swapContract.getNew().get(0).getTransactional().getReferences().get(0));
        System.out.println(swapContract.getNew().get(1).getTransactional().getReferences().get(0));

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionSnatch1() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }

        // stepa stole new revisions of contarcts and try to register it as single
        newDelorean.check();
        newDelorean.traceErrors();
        registerAndCheckDeclined(newDelorean);

        newLamborghini.check();
        newLamborghini.traceErrors();
        registerAndCheckDeclined(newLamborghini);

    }


    @Test(timeout = 90000)
    public void swapContractsViaTransactionSnatch2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }

        // marty stole new revisions of contracts and try to register it as single
        newDelorean.check();
        newDelorean.traceErrors();
        registerAndCheckDeclined(newDelorean);

        newLamborghini.check();
        newLamborghini.traceErrors();
        registerAndCheckDeclined(newLamborghini);

    }


    @Test(timeout = 60000)
    public void swapSplitJoinAllGood() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : martyPrivateKeys) {
            martyPublicKeys.add(pk.getPublicKey());
        }

        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract martyCoins = Contract.fromDslFile(ROOT_PATH + "martyCoins.yml");
        martyCoins.addSignerKey(martyPrivateKeys.iterator().next());
        martyCoins.seal();
        martyCoins.check();
        martyCoins.traceErrors();
        registerAndCheckApproved(martyCoins);

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();
        registerAndCheckApproved(stepaCoins);

        System.out.println("--- coins created ---");


        // 100 - 30 = 70
        Contract martyCoinsSplit = ContractsService.createSplit(martyCoins, 30, "amount", martyPrivateKeys);
        Contract martyCoinsSplitToStepa = martyCoinsSplit.getNew().get(0);
        Contract stepaCoinsSplit = ContractsService.createSplit(stepaCoins, 30, "amount", stepaPrivateKeys);
        Contract stepaCoinsSplitToMarty = stepaCoinsSplit.getNew().get(0);

        martyCoinsSplitToStepa.check();
        martyCoinsSplitToStepa.traceErrors();
        stepaCoinsSplitToMarty.check();
        stepaCoinsSplitToMarty.traceErrors();

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;
        swapContract = ContractsService.startSwap(martyCoinsSplitToStepa, stepaCoinsSplitToMarty, martyPrivateKeys, stepaPublicKeys, false);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.getNewItems().clear();
        swapContract.addNewItems(martyCoinsSplit, stepaCoinsSplit);
        swapContract.seal();
        swapContract.addSignatureToSeal(martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckApproved(swapContract);


        assertEquals(ItemState.APPROVED, node.waitItem(martyCoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(stepaCoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(martyCoinsSplitToStepa.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(stepaCoinsSplitToMarty.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(martyCoins.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(stepaCoins.getId(), 5000).state);
    }


    @Test(timeout = 90000)
    public void swapSplitJoinMissingSign() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : martyPrivateKeys) {
            martyPublicKeys.add(pk.getPublicKey());
        }

        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract martyCoins = Contract.fromDslFile(ROOT_PATH + "martyCoins.yml");
        martyCoins.addSignerKey(martyPrivateKeys.iterator().next());
        martyCoins.seal();
        martyCoins.check();
        martyCoins.traceErrors();
        registerAndCheckApproved(martyCoins);

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();
        registerAndCheckApproved(stepaCoins);

        System.out.println("--- coins created ---");


        // 100 - 30 = 70
        Contract martyCoinsSplit = ContractsService.createSplit(martyCoins, 30, "amount", martyPrivateKeys);
        // remove sign!!
        martyCoinsSplit.getKeysToSignWith().clear();
        martyCoinsSplit.removeAllSignatures();
        martyCoinsSplit.seal();
        Contract martyCoinsSplitToStepa = martyCoinsSplit.getNew().get(0);
        Contract stepaCoinsSplit = ContractsService.createSplit(stepaCoins, 30, "amount", stepaPrivateKeys);
        Contract stepaCoinsSplitToMarty = stepaCoinsSplit.getNew().get(0);

        martyCoinsSplit.check();
        martyCoinsSplit.traceErrors();
        martyCoinsSplitToStepa.check();
        martyCoinsSplitToStepa.traceErrors();
        stepaCoinsSplitToMarty.check();
        stepaCoinsSplitToMarty.traceErrors();

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;
        swapContract = ContractsService.startSwap(martyCoinsSplitToStepa, stepaCoinsSplitToMarty, martyPrivateKeys, stepaPublicKeys, false);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.getNewItems().clear();
        swapContract.addNewItems(martyCoinsSplit, stepaCoinsSplit);
        swapContract.seal();
        swapContract.addSignatureToSeal(martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);


        assertEquals(ItemState.UNDEFINED, node.waitItem(martyCoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(stepaCoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(martyCoinsSplitToStepa.getId(), 5000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(stepaCoinsSplitToMarty.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(martyCoins.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(stepaCoins.getId(), 5000).state);
    }



    @Test(timeout = 90000)
    public void registerParcel() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = createParcelWithFreshTU(stepaCoins, stepaPrivateKeys);

        parcel.getPayment().getContract().check();
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void registerParcelWithRealPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys, false);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKey());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100 - 1 , parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertFalse(parcel.getPayloadContract().isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }

    @Test(timeout = 90000)
    public void registerParcelWithTestPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKey());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }

//    @Test(timeout = 90000)
    public void declineParcelWithTooBigCostTUTestPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        PrivateKey stepaPrivateKey = stepaPrivateKeys.iterator().next();
        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        stepaCoins.addSignerKey(stepaPrivateKey);

        for (int i = 0; i < 100; i++) {
            Contract newItem = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
            newItem.setExpiresAt(ZonedDateTime.now().plusMonths(1));
            newItem.addSignerKey(stepaPrivateKey);
            stepaCoins.addNewItems(newItem);
        }
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        int processedCost = stepaCoins.getProcessedCostTU();
        System.out.println("stepaCoins processed cost in TU: " + processedCost);
        assertTrue(processedCost > Config.maxCostTUInTestMode);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, processedCost, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKey());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - processedCost, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertFalse(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 18000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 18000).state);
    }

    @Test(timeout = 90000)
    public void declineParcelWithTestPaymentBut4096Key() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        PrivateKey coinsKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract coins = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        coins.addSignerKey(coinsKey);
        coins.seal();
        coins.check();
        coins.traceErrors();

        assertFalse(coinsKey.getPublicKey().getBitStrength() == 2048);

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(coins, stepaTU, 1, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKey());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertFalse(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }

    @Test(timeout = 90000)
    public void declineParcelWithTestPaymentButTooFatherExpiration() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusYears(2));
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        assertTrue(stepaCoins.getExpiresAt().isAfter(ZonedDateTime.now().plusYears(1)));

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKey());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertFalse(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void declineParcelWithTestAndRealPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Contract stepaTU = InnerContractsService.createFreshTU(100, stepaPublicKeys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract paymentDecreased = stepaTU.createRevision(stepaPrivateKeys);

        paymentDecreased.getStateData().set("test_transaction_units", stepaTU.getStateData().getIntOrThrow("test_transaction_units") - 1);
        paymentDecreased.getStateData().set("transaction_units", stepaTU.getStateData().getIntOrThrow("transaction_units") - 1);
        paymentDecreased.seal();

        Parcel parcel = new Parcel(stepaCoins.getTransactionPack(), paymentDecreased.getTransactionPack());

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKey());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertFalse(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }


    @Test(timeout = 90000)
    public void registerParcelWithTestTUButRealPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKey());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isTU(config.getTransactionUnitsIssuerKey(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void declineParcelWithBadPayload() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
//        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = createParcelWithFreshTU(stepaCoins, stepaPrivateKeys);

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPayloadContract().isOk());

        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void declineParcelWithNotRegisteredPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();


        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract stepaTU = Contract.fromDslFile(ROOT_PATH + "StepaTU.yml");
        stepaTU.addSignerKey(manufacturePrivateKey);
        stepaTU.seal();
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
//        registerAndCheckApproved(stepaTU);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 50, stepaPrivateKeys);

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void declineParcelWithNotSignedPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();


        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/tu_key.private.unikey"));
        Contract stepaTU = Contract.fromDslFile(ROOT_PATH + "StepaTU.yml");
        stepaTU.addSignerKey(manufacturePrivateKey);
        stepaTU.seal();
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
//        registerAndCheckApproved(stepaTU);

        Contract paymentDecreased = stepaTU.createRevision();
        paymentDecreased.getStateData().set("transaction_units", stepaTU.getStateData().getIntOrThrow("transaction_units") - 50);

        //paymentDecreased.setIsTU(true);
        paymentDecreased.seal();
        paymentDecreased.check();
        paymentDecreased.traceErrors();

        Parcel parcel = new Parcel(stepaCoins.getTransactionPack(), paymentDecreased.getTransactionPack());

        assertFalse(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        node.registerParcel(parcel);
        // check parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPaymentContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(parcel.getPayloadContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void declineItemFromoutWhiteList() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        node.registerItem(stepaCoins);
        ItemResult itemResult = node.waitItem(stepaCoins.getId(), 18000);
        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }

    @Test
    public void referenceForChangeOwner() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract trustedManager = new Contract(llcPrivateKeys.iterator().next());
        trustedManager.setOwnerKeys(stepaPublicKeys);
        trustedManager.seal();

        registerAndCheckApproved(trustedManager);

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "NotaryWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        Reference ref = new Reference();
        ref.name = "ceritfication_contract";
        ref.type = Reference.TYPE_EXISTING;
        ref.contract_id = trustedManager.getId();
        llcProperty.getDefinition().getReferences().add(ref);
        llcProperty.seal();

        registerAndCheckApproved(llcProperty);

        Contract llcProperty2 = llcProperty.createRevision(stepaPrivateKeys);
        llcProperty2.setOwnerKeys(thirdPartyPublicKeys);
        llcProperty2.seal();
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        TransactionPack tp_before = llcProperty2.getTransactionPack();
        tp_before.addReference(trustedManager);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        Contract tu = getApprovedTUContract();
        // stepaPrivateKeys - is also U keys
        Parcel parcel =  ContractsService.createParcel(tp_after, tu, 150, stepaPrivateKeys);
        System.out.println("-------------");
        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        waitAndCheckApproved(parcel);
    }

    @Test
    public void referenceForRevoke() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract trustedManager = new Contract(llcPrivateKeys.iterator().next());
        trustedManager.setOwnerKeys(stepaPublicKeys);
        trustedManager.seal();

        registerAndCheckApproved(trustedManager);

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "NotaryWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        Reference ref = new Reference();
        ref.name = "ceritfication_contract";
        ref.type = Reference.TYPE_EXISTING;
        ref.contract_id = trustedManager.getId();
        llcProperty.getDefinition().getReferences().add(ref);
        llcProperty.seal();

        registerAndCheckApproved(llcProperty);

        Contract llcProperty2 = ContractsService.createRevocation(llcProperty, stepaPrivateKeys.iterator().next());
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());


        TransactionPack tp_before = llcProperty2.getTransactionPack();
        tp_before.addReference(trustedManager);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        Contract tu = getApprovedTUContract();
        // stepaPrivateKeys - is also U keys
        Parcel parcel =  ContractsService.createParcel(tp_after, tu, 150, stepaPrivateKeys);
        System.out.println("-------------");
        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        waitAndCheckApproved(parcel);
    }

    @Test
    public void referenceForSplitJoin() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract trustedManager = new Contract(llcPrivateKeys.iterator().next());
        trustedManager.setOwnerKeys(stepaPublicKeys);
        trustedManager.seal();

        registerAndCheckApproved(trustedManager);

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "TokenWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        Reference ref = new Reference();
        ref.name = "ceritfication_contract";
        ref.type = Reference.TYPE_EXISTING;
        ref.contract_id = trustedManager.getId();
        llcProperty.getDefinition().getReferences().add(ref);
        llcProperty.seal();

        registerAndCheckApproved(llcProperty);

        Contract llcProperty2 = ContractsService.createSplit(llcProperty, 100,
                "amount", stepaPrivateKeys, true);
//        llcProperty2.createRole("creator", llcProperty2.getRole("owner"));
//        llcProperty2.getNew().get(0).createRole("creator", llcProperty2.getNew().get(0).getRole("owner"));
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        TransactionPack tp_before = llcProperty2.getTransactionPack();
        tp_before.addReference(trustedManager);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        Contract tu = getApprovedTUContract();
        // stepaPrivateKeys - is also U keys
        Parcel parcel =  ContractsService.createParcel(tp_after, tu, 150, stepaPrivateKeys);
        System.out.println("-------------");
        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        waitAndCheckApproved(parcel);
    }

    @Test
    public void referenceForChangeNumber() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract trustedManager = new Contract(llcPrivateKeys.iterator().next());
        trustedManager.setOwnerKeys(stepaPublicKeys);
        trustedManager.seal();

        registerAndCheckApproved(trustedManager);

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "AbonementWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        Reference ref = new Reference();
        ref.name = "ceritfication_contract";
        ref.type = Reference.TYPE_EXISTING;
        ref.contract_id = trustedManager.getId();
        llcProperty.getDefinition().getReferences().add(ref);
        llcProperty.seal();

        registerAndCheckApproved(llcProperty);

        Contract llcProperty2 = llcProperty.createRevision(stepaPrivateKeys);
        llcProperty2.getStateData().set("units",
                llcProperty.getStateData().getIntOrThrow("units") - 1);
        llcProperty2.seal();
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        TransactionPack tp_before = llcProperty2.getTransactionPack();
        tp_before.addReference(trustedManager);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        Contract tu = getApprovedTUContract();
        // stepaPrivateKeys - is also U keys
        Parcel parcel =  ContractsService.createParcel(tp_after, tu, 150, stepaPrivateKeys);
        System.out.println("-------------");
        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        waitAndCheckApproved(parcel);
    }

    @Ignore("Stress test")
    @Test(timeout = 900000)
    public void testLedgerLocks() throws Exception {
        ExtendedSignatureTest.parallelize(Executors.newCachedThreadPool(), 4, () -> {
            try {
                Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
                Set<PublicKey> stepaPublicKeys = new HashSet<>();
                stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
                for (PrivateKey pk : stepaPrivateKeys) {
                    stepaPublicKeys.add(pk.getPublicKey());
                }
                PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/tu_key.private.unikey"));
                int N = 100;
                for (int i = 0; i < N; i++) {

                    Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
                    stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
                    stepaCoins.seal();

                    Parcel parcel = createParcelWithClassTU(stepaCoins, stepaPrivateKeys);
                    synchronized (tuContractLock) {
                        tuContract = parcel.getPaymentContract();
                    }

                    System.out.println("-------------- register parcel " + parcel.getId() + " (iteration " + i + ") ------------");
                    node.registerParcel(parcel);

                    for (Node n : nodes) {
                        n.waitParcel(parcel.getId(), 15000);
                        ItemResult itemResult = n.waitItem(stepaCoins.getId(), 15000);
                    }

                    ItemState itemState1 = node.waitItem(parcel.getPaymentContract().getRevoking().get(0).getId(), 15000).state;
                    ItemState itemState2 = node.getLedger().getRecord(parcel.getPaymentContract().getRevoking().get(0).getId()).getState();

                    System.out.println("--- check item " + parcel.getPaymentContract().getRevoking().get(0).getId() + " --- iteration " + i);
                    System.out.println("state from node: " + itemState1);
                    System.out.println("state from ledger: " + itemState2);
                    assertEquals(itemState1, itemState2);
                    assertEquals(ItemState.REVOKED, itemState1);
                    assertEquals(ItemState.REVOKED, itemState2);
                }
            } catch (Exception e) {
                System.out.println("exception: " + e.toString());
            }
        });
    }


    public synchronized Parcel createParcelWithFreshTU(Contract c, Set<PrivateKey> keys) throws Exception {

        Set<PublicKey> ownerKeys = new HashSet();
        keys.stream().forEach(key -> ownerKeys.add(key.getPublicKey()));
        Contract stepaTU = InnerContractsService.createFreshTU(100000000, ownerKeys);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        return ContractsService.createParcel(c, stepaTU, 150, keys);
    }

    protected Contract getApprovedTUContract() throws Exception {
        synchronized (tuContractLock) {
            if (tuContract == null) {
                PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
                Set<PublicKey> keys = new HashSet();
                keys.add(ownerKey.getPublicKey());
                Contract stepaTU = InnerContractsService.createFreshTU(100000000, keys);
                stepaTU.check();
                stepaTU.traceErrors();
                System.out.println("register new TU ");
                node.registerItem(stepaTU);
                tuContract = stepaTU;
            }
            int needRecreateTuContractNum = 0;
            for (Node n : nodes) {
                try {
                    ItemResult itemResult = n.waitItem(tuContract.getId(), 15000);
                    //assertEquals(ItemState.APPROVED, itemResult.state);
                    if (itemResult.state != ItemState.APPROVED) {
                        System.out.println("TU: node " + n + " result: " + itemResult);
                        needRecreateTuContractNum ++;
                    }
                } catch (TimeoutException e) {
                    System.out.println("ping ");
//                    System.out.println(n.ping());
////                    System.out.println(n.traceTasksPool());
//                    System.out.println(n.traceParcelProcessors());
//                    System.out.println(n.traceItemProcessors());
                    System.out.println("TU: node " + n + " timeout: ");
                    needRecreateTuContractNum ++;
                }
            }
            int recreateBorder = nodes.size() - config.getPositiveConsensus() - 1;
            if(recreateBorder < 0)
                recreateBorder = 0;
            if (needRecreateTuContractNum > recreateBorder) {
                tuContract = null;
                Thread.sleep(1000);
                return getApprovedTUContract();
            }
            return tuContract;
        }
    }

    public synchronized Parcel createParcelWithClassTU(Contract c, Set<PrivateKey> keys) throws Exception {
        Contract tu = getApprovedTUContract();
        Parcel parcel =  ContractsService.createParcel(c, tu, 150, keys);
//        System.out.println("create  parcel: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
        return parcel;
    }

    protected synchronized Parcel registerWithNewParcel(Contract c) throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel parcel = createParcelWithClassTU(c, stepaPrivateKeys);
        System.out.println("register  parcel: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        return parcel;
    }

    private synchronized void registerAndCheckApproved(Contract c) throws Exception {
        Parcel parcel = registerWithNewParcel(c);
        waitAndCheckApproved(parcel);
    }

    private synchronized void waitAndCheckApproved(Parcel parcel) throws Exception {
        try {
//            LogPrinter.showDebug(true);
            System.out.println("registerAndCheckApproved, wait parcel: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            node.waitParcel(parcel.getId(), 30000);
            System.out.println("registerAndCheckApproved, wait payment: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            ItemResult itemResult = node.waitItem(parcel.getPaymentContract().getId(), 8000);
            assertEquals(ItemState.APPROVED, itemResult.state);
            System.out.println("registerAndCheckApproved, wait payload: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            itemResult = node.waitItem(parcel.getPayloadContract().getId(), 8000);
            assertEquals(ItemState.APPROVED, itemResult.state);
        } catch (TimeoutException e) {
            if (parcel != null) {
                fail("timeout,  " + node + " parcel " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            } else {
                fail("timeout,  " + node);
            }
        }
    }

    private synchronized void registerAndCheckDeclined(Contract c) throws Exception {
        Parcel parcel = null;
        try {
            parcel = registerWithNewParcel(c);
    //        LogPrinter.showDebug(true);
            System.out.println("registerAndCheckDeclined, wait parcel: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            node.waitParcel(parcel.getId(), 30000);
            System.out.println("registerAndCheckDeclined, wait payment: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            ItemResult itemResult = node.waitItem(parcel.getPaymentContract().getId(), 8000);
            assertEquals(ItemState.APPROVED, itemResult.state);
            System.out.println("registerAndCheckDeclined, wait payload: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            itemResult = node.waitItem(parcel.getPayloadContract().getId(), 8000);
            assertEquals(ItemState.DECLINED, itemResult.state);
        } catch (TimeoutException e) {
            System.out.println("ping ");
//            System.out.println(node.ping());
////            System.out.println(node.traceTasksPool());
//            for (Node n : nodes) {
//                System.out.println(n + " " + n.traceParcelProcessors());
//                System.out.println(n + " " + n.traceItemProcessors());
//            }
            if (parcel != null) {
                fail("timeout,  " + node + " parcel " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            } else {
                fail("timeout,  " + node);
            }
        }
    }

    /**
     * Imitate of sending contract from one part of swappers to another.
     *
     * Method packs sending contracts with main swap contract (can be blank - doesn't matter) into TransactionPack.
     * Then restore from packed binary main swap contract, contracts sending with.
     *
     * @param mainContract
     * @return
     * @throws Exception
     */
    public synchronized Contract imitateSendingTransactionToPartner(Contract mainContract) throws Exception {

        TransactionPack tp_before = mainContract.getTransactionPack();
        byte[] data = tp_before.pack();

        // here we "send" data and "got" it

        TransactionPack tp_after = TransactionPack.unpack(data);
        Contract gotMainContract = tp_after.getContract();

        return gotMainContract;
    }



    protected synchronized void addDetailsToAllLedgers(Contract contract) {
        HashId id;
        StateRecord orCreate;
        for (Approvable c : contract.getRevokingItems()) {
            id = c.getId();
            for (Node nodeS : nodesMap.values()) {
                orCreate = nodeS.getLedger().findOrCreate(id);
                orCreate.setState(ItemState.APPROVED).save();
            }
        }

        destroyFromAllNodesExistingNew(contract);

        destroyCurrentFromAllNodesIfExists(contract);
    }

    protected synchronized void destroyFromAllNodesExistingNew(Contract c50_1) {
        StateRecord orCreate;
        for (Approvable c : c50_1.getNewItems()) {
            for (Node nodeS : nodesMap.values()) {
                orCreate = nodeS.getLedger().getRecord(c.getId());
                if (orCreate != null)
                    orCreate.destroy();
            }
        }
    }

    protected synchronized void destroyCurrentFromAllNodesIfExists(Contract finalC) {
        for (Node nodeS : nodesMap.values()) {
            StateRecord r = nodeS.getLedger().getRecord(finalC.getId());
            if (r != null) {
                r.destroy();
            }
        }
    }



    @Test
    public void itemsCachedThenPurged() throws Exception {

        // todo: rewrite
//        config.setMaxElectionsTime(Duration.ofMillis(100));
//
//        TestItem main = new TestItem(true);
//        main.setExpiresAtPlusFive(false);
//
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 1500);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//        assertEquals(ItemState.UNDEFINED, node.checkItem(main.getId()).state);
//
//        assertEquals(main, node.getItem(main.getId()));
//        Thread.sleep(500);
//        assertEquals(ItemState.UNDEFINED, node.checkItem(main.getId()).state);
    }



    public synchronized Contract startSwap_wrongKey(Contract contract1, Contract contract2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys, PrivateKey wrongKey) {

        Set<PublicKey> fromPublicKeys = new HashSet<>();
        for (PrivateKey pk : fromKeys) {
            fromPublicKeys.add(pk.getPublicKey());
        }

        // first of all we creating main swap contract which will include new revisions of contract for swap
        // you can think about this contract as about transaction
        Contract swapContract = new Contract();

        Contract.Definition cd = swapContract.getDefinition();
        // by default, transactions expire in 30 days
        cd.setExpiresAt(swapContract.getCreatedAt().plusDays(30));

        SimpleRole issuerRole = new SimpleRole("issuer");
        for (PrivateKey k : fromKeys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            issuerRole.addKeyRecord(kr);
        }

        swapContract.registerRole(issuerRole);
        swapContract.registerRole((issuerRole).linkAs("owner"));
        swapContract.registerRole((issuerRole).linkAs("creator"));

        // now we will prepare new revisions of contracts

        // create new revisions of contracts and create transactional sections in it

        Contract newContract1 = contract1.createRevision(wrongKey);
        Contract.Transactional transactional1 = newContract1.createTransactionalSection();
        transactional1.setId(HashId.createRandom().toBase64String());

        Contract newContract2 = contract2.createRevision();
        Contract.Transactional transactional2 = newContract2.createTransactionalSection();
        transactional2.setId(HashId.createRandom().toBase64String());


        // prepare roles for references
        // it should new owners and old creators in new revisions of contracts

        SimpleRole ownerFrom = new SimpleRole("owner");
        SimpleRole creatorFrom = new SimpleRole("creator");
        for (PrivateKey k : fromKeys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            ownerFrom.addKeyRecord(kr);
            creatorFrom.addKeyRecord(kr);
        }

        SimpleRole ownerTo = new SimpleRole("owner");
        SimpleRole creatorTo = new SimpleRole("creator");
        for (PublicKey k : toKeys) {
            KeyRecord kr = new KeyRecord(k);
            ownerTo.addKeyRecord(kr);
            creatorTo.addKeyRecord(kr);
        }


        // create references for contracts that point to each other and asks correct signs

        Reference reference1 = new Reference();
        reference1.transactional_id = transactional2.getId();
        reference1.type = Reference.TYPE_TRANSACTIONAL;
        reference1.required = true;
        reference1.signed_by = new ArrayList<>();
        reference1.signed_by.add(ownerFrom);
        reference1.signed_by.add(creatorTo);

        Reference reference2 = new Reference();
        reference2.transactional_id = transactional1.getId();
        reference2.type = Reference.TYPE_TRANSACTIONAL;
        reference2.required = true;
        reference2.signed_by = new ArrayList<>();
        reference2.signed_by.add(ownerTo);
        reference2.signed_by.add(creatorFrom);

        // and add this references to existing transactional section
        transactional1.addReference(reference1);
        transactional2.addReference(reference2);


        // swap owners in this contracts
        newContract1.setOwnerKeys(toKeys);
        newContract2.setOwnerKeys(fromPublicKeys);

        newContract1.seal();
        newContract2.seal();

        // finally on this step add created new revisions to main swap contract
        swapContract.addNewItems(newContract1);
        swapContract.addNewItems(newContract2);
        swapContract.seal();

        return swapContract;
    }

    public synchronized Contract signPresentedSwap_wrongKey(Contract swapContract, Set<PrivateKey> keys, PrivateKey wrongKey) {

        Set<PublicKey> publicKeys = new HashSet<>();
        for (PrivateKey pk : keys) {
            publicKeys.add(pk.getPublicKey());
        }

        List<Contract> swappingContracts = (List<Contract>) swapContract.getNew();

        // looking for contract that will be own and sign it
        HashId contractHashId = null;
        for (Contract c : swappingContracts) {
            boolean willBeMine = c.getOwner().isAllowedForKeys(publicKeys);

            if(willBeMine) {
                c.addSignatureToSeal(wrongKey);
                contractHashId = c.getId();
            }
        }

        // looking for contract that was own, add to reference hash of above contract and sign it
        for (Contract c : swappingContracts) {
            boolean willBeNotMine = (!c.getOwner().isAllowedForKeys(publicKeys));

            if(willBeNotMine) {

                Set<KeyRecord> krs = new HashSet<>();
                for (PublicKey k: publicKeys) {
                    krs.add(new KeyRecord(k));
                }
                c.setCreator(krs);

                if(c.getTransactional() != null && c.getTransactional().getReferences() != null) {
                    for (Reference rm : c.getTransactional().getReferences()) {
                        rm.contract_id = contractHashId;
                    }
                } else {
                    return swapContract;
                }

                c.seal();
                c.addSignatureToSeal(wrongKey);
            }
        }

        swapContract.seal();
        return swapContract;
    }

    public synchronized Contract finishSwap_wrongKey(Contract swapContract, Set<PrivateKey> keys, PrivateKey wrongKey) {

        List<Contract> swappingContracts = (List<Contract>) swapContract.getNew();

        // looking for contract that will be own
        for (Contract c : swappingContracts) {
            boolean willBeMine = c.getOwner().isAllowedForKeys(keys);
            System.out.println("willBeMine: " + willBeMine + " " + c.getSealedByKeys().size());
            if(willBeMine) {
                c.addSignatureToSeal(wrongKey);
            }
        }

        swapContract.seal();
        swapContract.addSignatureToSeal(keys);

        return swapContract;
    }

    @Ignore("parallel test")
    @Test
    public void parallelTest() throws Exception {
//        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
//        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());
        PrivateKey myKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

//        assertEquals(main.cache, main.node.getCache());
//        ItemCache c1 = main.cache;
//        ItemCache c2 = main.node.getCache();

//        Client client = new Client(myKey, main.myInfo, null);


        List<Contract> contractsForThreads = new ArrayList<>();
        int N = 100;
        int M = 2;
        float threshold = 1.2f;
        float ratio = 0;
        boolean createNewContracts = true;
//        assertTrue(singleContract.isOk());

//        ItemResult r = client.getState(singleContract.getId());
//        assertEquals(ItemState.UNDEFINED, r.state);
//        System.out.println(r);


        contractsForThreads = new ArrayList<>();
        for(int j = 0; j < M; j++) {
            Contract contract = new Contract(myKey);

            for (int k = 0; k < 10; k++) {
                Contract nc = new Contract(myKey);
                nc.seal();
                contract.addNewItems(nc);
            }
            contract.seal();
            assertTrue(contract.isOk());
            contractsForThreads.add(contract);

//            ItemResult r = client.getState(contract.getId());
//            assertEquals(ItemState.UNDEFINED, r.state);
//            System.out.println(r);
        }

        Contract singleContract = new Contract(myKey);

        for (int k = 0; k < 10; k++) {
            Contract nc = new Contract(myKey);
            nc.seal();
            singleContract.addNewItems(nc);
        }
        singleContract.seal();

        // register


        for(int i = 0; i < N; i++) {

            if(createNewContracts) {
                contractsForThreads = new ArrayList<>();
                for(int j = 0; j < M; j++) {
                    Contract contract = new Contract(myKey);

                    for (int k = 0; k < 10; k++) {
                        Contract nc = new Contract(myKey);
                        nc.seal();
                        contract.addNewItems(nc);
                    }
                    contract.seal();
                    assertTrue(contract.isOk());
                    contractsForThreads.add(contract);


                }

                singleContract = new Contract(myKey);

                for (int k = 0; k < 10; k++) {
                    Contract nc = new Contract(myKey);
                    nc.seal();
                    singleContract.addNewItems(nc);
                }
                singleContract.seal();
            }

            long ts1;
            long ts2;
            Semaphore semaphore = new Semaphore(-(M-1));

            ts1 = new Date().getTime();

            for(Contract c : contractsForThreads) {
                Thread thread = new Thread(() -> {

                    long t = System.nanoTime();
                    ItemResult rr = null;
                    rr = node.registerItem(c);
                    try {
                        rr = node.waitItem(c.getId(), 15000);
                    } catch (TimeoutException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("multi thread: " + rr + " time: " + ((System.nanoTime() - t) * 1e-9));
                    semaphore.release();
                });
                thread.setName("Multi-thread register: " + c.getId().toString());
                thread.start();
            }

            semaphore.acquire();

            ts2 = new Date().getTime();

            long threadTime = ts2 - ts1;

            //

            ts1 = new Date().getTime();

            Contract finalSingleContract = singleContract;
            Thread thread = new Thread(() -> {
                long t = System.nanoTime();
                ItemResult rr = null;
                rr = node.registerItem(finalSingleContract);
                try {
                    rr = node.waitItem(finalSingleContract.getId(), 15000);
                } catch (TimeoutException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("single thread: " + rr + " time: " + ((System.nanoTime() - t) * 1e-9));
                semaphore.release();
            });
            thread.setName("single-thread register: " + singleContract.getId().toString());
            thread.start();

            semaphore.acquire();

            ts2 = new Date().getTime();

            long singleTime = ts2 - ts1;

            System.out.println(threadTime * 1.0f / singleTime);
            ratio += threadTime * 1.0f / singleTime;
        }

        ratio /= N;
        System.out.println("average " + ratio);
    }


    @Ignore("parallel test")
    @Test
    public void parallelContractNodeCheck() throws Exception {
//        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
//        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());
        PrivateKey myKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

//        assertEquals(main.cache, main.node.getCache());
//        ItemCache c1 = main.cache;
//        ItemCache c2 = main.node.getCache();

//        Client client = new Client(myKey, main.myInfo, null);


        List<Contract> contractsForThreads = new ArrayList<>();
        int N = 100;
        int M = 2;
        float threshold = 1.2f;
        float ratio = 0;
        boolean createNewContracts = true;
//        assertTrue(singleContract.isOk());

//        ItemResult r = client.getState(singleContract.getId());
//        assertEquals(ItemState.UNDEFINED, r.state);
//        System.out.println(r);


        contractsForThreads = new ArrayList<>();
        for(int j = 0; j < M; j++) {
            Contract contract = new Contract(myKey);

            for (int k = 0; k < 10; k++) {
                Contract nc = new Contract(myKey);
                nc.seal();
                contract.addNewItems(nc);
            }
            contract.seal();
            assertTrue(contract.isOk());
            contractsForThreads.add(contract);

//            ItemResult r = client.getState(contract.getId());
//            assertEquals(ItemState.UNDEFINED, r.state);
//            System.out.println(r);
        }

        Contract singleContract = new Contract(myKey);

        for (int k = 0; k < 10; k++) {
            Contract nc = new Contract(myKey);
            nc.seal();
            singleContract.addNewItems(nc);
        }
        singleContract.seal();

        // register


        for(int i = 0; i < N; i++) {

            if(createNewContracts) {
                contractsForThreads = new ArrayList<>();
                for(int j = 0; j < M; j++) {
                    Contract contract = new Contract(myKey);

                    for (int k = 0; k < 10; k++) {
                        Contract nc = new Contract(myKey);
                        nc.seal();
                        contract.addNewItems(nc);
                    }
                    contract.seal();
                    assertTrue(contract.isOk());
                    contractsForThreads.add(contract);


                }

                singleContract = new Contract(myKey);

                for (int k = 0; k < 10; k++) {
                    Contract nc = new Contract(myKey);
                    nc.seal();
                    singleContract.addNewItems(nc);
                }
                singleContract.seal();
            }

            long ts1;
            long ts2;
            Semaphore semaphore = new Semaphore(-(M-1));

            ts1 = new Date().getTime();

            for(Contract c : contractsForThreads) {
                Thread thread = new Thread(() -> {

                    long t = System.nanoTime();
                    try {
                        List<StateRecord> lockedToCreate = new ArrayList<>();
                        List<StateRecord> lockedToRevoke = new ArrayList<>();
                        StateRecord record = node.getLedger().findOrCreate(c.getId());
                        c.check();
                        isNeedToResync(true, c);
                        checkSubItemsOf(c, record, lockedToCreate, lockedToRevoke);
                    } catch (Quantiser.QuantiserException e) {
                        e.printStackTrace();
                    }

                    System.out.println("multi thread: time: " + ((System.nanoTime() - t) * 1e-9));
                    semaphore.release();
                });
                thread.setName("Multi-thread register: " + c.getId().toString());
                thread.start();
            }

            semaphore.acquire();

            ts2 = new Date().getTime();

            long threadTime = ts2 - ts1;

            //

            ts1 = new Date().getTime();

            Contract finalSingleContract = singleContract;
            Thread thread = new Thread(() -> {
                long t = System.nanoTime();
                try {
                    List<StateRecord> lockedToCreate = new ArrayList<>();
                    List<StateRecord> lockedToRevoke = new ArrayList<>();
                    StateRecord record = node.getLedger().findOrCreate(finalSingleContract.getId());
                    finalSingleContract.check();
                    isNeedToResync(true, finalSingleContract);
                    checkSubItemsOf(finalSingleContract, record, lockedToCreate, lockedToRevoke);
                } catch (Quantiser.QuantiserException e) {
                    e.printStackTrace();
                }

                System.out.println("single thread: time: " + ((System.nanoTime() - t) * 1e-9));
                semaphore.release();
            });
            thread.setName("single-thread register: " + singleContract.getId().toString());
            thread.start();

            semaphore.acquire();

            ts2 = new Date().getTime();

            long singleTime = ts2 - ts1;

            System.out.println(threadTime * 1.0f / singleTime);
            ratio += threadTime * 1.0f / singleTime;
        }

        ratio /= N;
        System.out.println("average " + ratio);
    }

    public HashMap<HashId, StateRecord> isNeedToResync(boolean baseCheckPassed, Approvable item) {
        HashMap<HashId, StateRecord> unknownParts = new HashMap<>();
        HashMap<HashId, StateRecord> knownParts = new HashMap<>();
        if (baseCheckPassed) {
            // check the referenced items
            for (Reference refModel : item.getReferences().values()) {
                HashId id = refModel.contract_id;
                if(refModel.type == Reference.TYPE_EXISTING && id != null) {
                    StateRecord r = node.getLedger().getRecord(id);

                    if (r == null || !r.getState().isConsensusFound()) {
                        unknownParts.put(id, r);
                    } else {
                        knownParts.put(id, r);
                    }
                }
            }
            // check revoking items
            for (Approvable a : item.getRevokingItems()) {
                StateRecord r = node.getLedger().getRecord(a.getId());

                if (r == null || !r.getState().isConsensusFound()) {
                    unknownParts.put(a.getId(), r);
                } else {
                    knownParts.put(a.getId(), r);
                }
            }
        } else {
        }
        boolean needToResync = false;
        // contract is complex and consist from parts
        if (unknownParts.size() + knownParts.size() > 0) {
            needToResync = baseCheckPassed &&
                    unknownParts.size() > 0 &&
                    knownParts.size() >= config.getKnownSubContractsToResync();
        }

        if (needToResync)
            return unknownParts;
        return new HashMap<>();
    }

    // check subitems of given item recursively (down for newItems line)
    private final void checkSubItemsOf(Approvable checkingItem, StateRecord record, List<StateRecord> lockedToCreate, List<StateRecord> lockedToRevoke) {
        for (Reference refModel : checkingItem.getReferences().values()) {
            HashId id = refModel.contract_id;
            if (refModel.type != Reference.TYPE_TRANSACTIONAL) {
                if (!node.getLedger().isApproved(id)) {
                    checkingItem.addError(Errors.BAD_REF, id.toString(), "reference not approved");
                }
            }
        }
        // check revoking items
        for (Approvable a : checkingItem.getRevokingItems()) {
            StateRecord r = record.lockToRevoke(a.getId());
            if (r == null) {
                checkingItem.addError(Errors.BAD_REVOKE, a.getId().toString(), "can't revoke");
            } else {
                if (!lockedToRevoke.contains(r))
                    lockedToRevoke.add(r);
            }
        }
        // check new items
        for (Approvable newItem : checkingItem.getNewItems()) {

            checkSubItemsOf(newItem, record, lockedToCreate, lockedToRevoke);

            if (!newItem.getErrors().isEmpty()) {
                checkingItem.addError(Errors.BAD_NEW_ITEM, newItem.getId().toString(), "bad new item: not passed check");
            } else {
                StateRecord r = record.createOutputLockRecord(newItem.getId());
                if (r == null) {
                    checkingItem.addError(Errors.NEW_ITEM_EXISTS, newItem.getId().toString(), "new item exists in ledger");
                } else {
                    if (!lockedToCreate.contains(r))
                        lockedToCreate.add(r);
                }
            }
        }
    }

    @Test
    @Ignore("it is snatch test")
    public void splitSnatch() throws Exception {
        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        assertTrue(c1.check());
        c1.seal();
        registerAndCheckApproved(c1);
//        Contract c1copy = new Contract(c1.getLastSealedBinary());
        System.out.println("money before split (c1): " + c1.getStateData().getIntOrThrow("amount"));
        Contract c2 = c1.splitValue("amount", new Decimal(50));
        System.out.println("money after split (c1): " + c1.getStateData().getIntOrThrow("amount"));
        System.out.println("money after split (c2): " + c2.getStateData().getIntOrThrow("amount"));
//        c2.addRevokingItems(c1copy);
        c2.getStateData().set("amount", 9000);//150);
        c2.seal();
        System.out.println("money after snatch (c2): " + c2.getStateData().getIntOrThrow("amount"));
        System.out.println("check after snatch (c2): " + c2.check());
        registerAndCheckDeclined(c2);
    }



    @Test
    @Ignore("it is snatch test")
    public void splitSnatch2() throws Exception {
        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        assertTrue(c1.check());
        c1.seal();
        registerAndCheckApproved(c1);
//        Contract c1copy = new Contract(c1.getLastSealedBinary());
//        Contract c1copy = new Contract(c1.getLastSealedBinary());
        System.out.println("true money origin: " + c1.getOrigin().toBase64String());
        Contract c1copy = new Contract(c1.getLastSealedBinary());

        System.out.println("money before split (c1): " + c1.getStateData().getIntOrThrow("amount"));
        c1 = c1.createRevision();
        Contract c2 = c1.splitValue("amount", new Decimal(60));
        System.out.println("money after split (c1): " + c1.getStateData().getIntOrThrow("amount"));
        System.out.println("money after split (c2): " + c2.getStateData().getIntOrThrow("amount"));
        System.out.println("check after split (c1.origin): " + c1.getOrigin().toBase64String());
        System.out.println("check after split (c2.origin): " + c2.getOrigin().toBase64String());
        ((Contract)c1.getRevokingItems().iterator().next()).getStateData().set("amount", 2000);
        c1.addSignerKey(key);
        c1.seal();
        c2.getStateData().set("amount", 1960);
        c2.addSignerKey(key);
        c2.seal();
        System.out.println("money after snatch (c2): " + c2.getStateData().getIntOrThrow("amount"));
        System.out.println("check after snatch (c2): " + c2.check());
//        registerAndCheckApproved(c1);
//        registerAndCheckApproved(c2);
        System.out.println("check after snatch (c1.origin): " + c1.getOrigin().toBase64String());
        System.out.println("check after snatch (c2.origin): " + c2.getOrigin().toBase64String());
        System.out.println("check after snatch (c1copy.origin): " + c1copy.getOrigin().toBase64String());
        registerAndCheckDeclined(c2);
    }



    @Test
    @Ignore("it is snatch test")
    public void joinSnatch() throws Exception {
        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> keys = new HashSet<>();
        keys.add(key);

        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        assertTrue(c1.check());
        c1.seal();
        registerAndCheckApproved(c1);

        System.out.println("money before split (c1): " + c1.getStateData().getIntOrThrow("amount"));
        Contract c2 = ContractsService.createSplit(c1, 99, "amount", keys);
        Contract c3 = c2.getNew().get(0);

        System.out.println("money after split (c2): " + c2.getStateData().getIntOrThrow("amount"));
        System.out.println("money after split (c3): " + c3.getStateData().getIntOrThrow("amount"));

        registerAndCheckApproved(c3);


        Contract c4 = c3.createRevision(keys);
        c4.addRevokingItems(c1);
        c4.getStateData().set("amount", 199);//150);
        c4.seal();
        System.out.println("money after snatch (c4): " + c4.getStateData().getIntOrThrow("amount"));
        System.out.println("check after snatch (c4): " + c4.check());
        c4.traceErrors();
        registerAndCheckDeclined(c4);
    }



    protected synchronized Contract checkPayment_preparePaymentContract(Set<PrivateKey> privateKeys) throws Exception {
        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(privateKeys.iterator().next());
        stepaCoins.seal();
        registerAndCheckApproved(stepaCoins);
        Parcel parcel = createParcelWithFreshTU(stepaCoins, privateKeys);
        return parcel.getPaymentContract();
    }



    protected synchronized Set<PrivateKey> checkPayment_preparePrivateKeys() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        return stepaPrivateKeys;
    }



    @Test(timeout = 90000)
    public void checkPayment_good() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKey());
        payment.traceErrors();
        assertTrue(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_zeroTU() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        payment.getStateData().set("transaction_units", 0);
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKey());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_wrongTUtype() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        payment.getStateData().set("transaction_units", "33");
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKey());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_wrongTUname() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        payment.getStateData().set("transacti0n_units", payment.getStateData().get("transaction_units"));
        payment.getStateData().remove("transaction_units");
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKey());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_missingDecrementPermission() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        payment.getPermissions().remove("decrement_permission");
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKey());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_wrongIssuer() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());

        PrivateKey manufactureFakePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
        SimpleRole issuerRole = new SimpleRole("issuer");
        KeyRecord kr = new KeyRecord(manufactureFakePrivateKey.getPublicKey());
        issuerRole.addKeyRecord(kr);
        payment.registerRole(issuerRole);

        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKey());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 20000)
    public void checkPayment_revision1() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        final Field field = payment.getState().getClass().getDeclaredField("revision");
        field.setAccessible(true);
        field.set(payment.getState(), 1);
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKey());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_originItself() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        final Field field = payment.getState().getClass().getDeclaredField("origin");
        field.setAccessible(true);
        field.set(payment.getState(), payment.getId());
        final Field field2 = payment.getRevoking().get(0).getState().getClass().getDeclaredField("origin");
        field2.setAccessible(true);
        field2.set(payment.getRevoking().get(0).getState(), payment.getId());
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKey());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_originMismatch() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        final Field field2 = payment.getRevoking().get(0).getState().getClass().getDeclaredField("origin");
        field2.setAccessible(true);
        field2.set(payment.getRevoking().get(0).getState(), payment.getId());
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKey());
        payment.traceErrors();
        assertFalse(res);
    }

}
