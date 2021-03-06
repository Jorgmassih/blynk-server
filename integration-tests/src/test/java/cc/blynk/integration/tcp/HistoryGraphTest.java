package cc.blynk.integration.tcp;

import cc.blynk.integration.BaseTest;
import cc.blynk.integration.SingleServerInstancePerTest;
import cc.blynk.integration.model.tcp.TestAppClient;
import cc.blynk.server.core.dao.ReportingDiskDao;
import cc.blynk.server.core.model.DataStream;
import cc.blynk.server.core.model.device.BoardType;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.device.Status;
import cc.blynk.server.core.model.device.Tag;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.serialization.JsonParser;
import cc.blynk.server.core.model.widgets.Widget;
import cc.blynk.server.core.model.widgets.outputs.graph.AggregationFunctionType;
import cc.blynk.server.core.model.widgets.outputs.graph.EnhancedHistoryGraph;
import cc.blynk.server.core.model.widgets.outputs.graph.FontSize;
import cc.blynk.server.core.model.widgets.outputs.graph.GraphDataStream;
import cc.blynk.server.core.model.widgets.outputs.graph.GraphGranularityType;
import cc.blynk.server.core.model.widgets.outputs.graph.GraphPeriod;
import cc.blynk.server.core.model.widgets.outputs.graph.GraphType;
import cc.blynk.server.core.model.widgets.ui.tiles.DeviceTiles;
import cc.blynk.server.core.model.widgets.ui.tiles.templates.PageTileTemplate;
import cc.blynk.server.core.protocol.model.messages.BinaryMessage;
import cc.blynk.server.core.protocol.model.messages.ResponseMessage;
import cc.blynk.server.core.protocol.model.messages.common.HardwareMessage;
import cc.blynk.server.workers.HistoryGraphUnusedPinDataCleanerWorker;
import cc.blynk.server.workers.ReportingTruncateWorker;
import cc.blynk.utils.FileUtils;
import cc.blynk.utils.ReportingUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;

import static cc.blynk.integration.TestUtil.b;
import static cc.blynk.integration.TestUtil.createDevice;
import static cc.blynk.integration.TestUtil.createTag;
import static cc.blynk.integration.TestUtil.illegalCommand;
import static cc.blynk.integration.TestUtil.ok;
import static cc.blynk.server.core.model.serialization.JsonParser.MAPPER;
import static cc.blynk.server.core.protocol.enums.Response.NO_DATA;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/2/2015.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class HistoryGraphTest extends SingleServerInstancePerTest {

    private static String blynkTempDir;

    @BeforeClass
    public static void initTempFolder() {
        blynkTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "blynk").toString();
    }

    @Test
    public void testGetGraphDataFor1Pin() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphGranularityType.HOURLY));

        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        clientPair.appClient.send("getgraphdata 1 d 8 24 h");

        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(2, bb.getInt());
        assertEquals(1.11D, bb.getDouble(), 0.1);
        assertEquals(1111111, bb.getLong());
        assertEquals(1.22D, bb.getDouble(), 0.1);
        assertEquals(2222222, bb.getLong());
    }

    @Test
    public void testTooManyDataForGraphWorkWithNewProtocol() throws Exception {
        TestAppClient appClient = new TestAppClient(properties);
        appClient.start();
        appClient.login(getUserName(), "1", "Android", "2.18.0");
        appClient.verifyResult(ok(1));

        String tempDir = holder.props.getProperty("data.folder");

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream1 = new DataStream((byte) 8, PinType.DIGITAL);
        DataStream dataStream2 = new DataStream((byte) 9, PinType.DIGITAL);
        DataStream dataStream3 = new DataStream((byte) 10, PinType.DIGITAL);
        DataStream dataStream4 = new DataStream((byte) 11, PinType.DIGITAL);
        GraphDataStream graphDataStream1 = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream1, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        GraphDataStream graphDataStream2 = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream2, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        GraphDataStream graphDataStream3 = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream3, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        GraphDataStream graphDataStream4 = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream4, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream1,
                graphDataStream2,
                graphDataStream3,
                graphDataStream4
        };

        appClient.createWidget(1, enhancedHistoryGraph);
        appClient.verifyResult(ok(1));
        appClient.reset();

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath1 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphPeriod.THREE_MONTHS.granularityType));
        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 9, GraphPeriod.THREE_MONTHS.granularityType));
        Path pinReportingDataPath3 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 10, GraphPeriod.THREE_MONTHS.granularityType));
        Path pinReportingDataPath4 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 11, GraphPeriod.THREE_MONTHS.granularityType));

        for (int i = 0; i < GraphPeriod.THREE_MONTHS.numberOfPoints; i++) {
            long now = System.currentTimeMillis();
            FileUtils.write(pinReportingDataPath1, ThreadLocalRandom.current().nextDouble(), now);
            FileUtils.write(pinReportingDataPath2, ThreadLocalRandom.current().nextDouble(), now);
            FileUtils.write(pinReportingDataPath3, ThreadLocalRandom.current().nextDouble(), now);
            FileUtils.write(pinReportingDataPath4, ThreadLocalRandom.current().nextDouble(), now);
        }

        appClient.reset();
        appClient.getEnhancedGraphData(1, 432, GraphPeriod.THREE_MONTHS);
        BinaryMessage graphDataResponse = appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        assertNotNull(decompressedGraphData);
    }

    @Test
    public void testDeleteGraphCommandWorks() throws Exception {
        clientPair.appClient.send("getgraphdata 1 d 8 del");

        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(ok(1)));
    }

    @Test
    public void testGetGraphDataForTagAndForEnhancedGraph1StreamWithoutData() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        Tag tag0 = new Tag(100_000, "Tag1", new int[] {0,1});

        clientPair.appClient.createTag(1, tag0);
        String createdTag = clientPair.appClient.getBody(2);
        Tag tag = JsonParser.parseTag(createdTag, 0);
        assertNotNull(tag);
        assertEquals(100_000, tag.id);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createTag(2, tag)));


        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        DataStream dataStream2 = new DataStream((byte) 89, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 100_000, dataStream, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        GraphDataStream graphDataStream2 = new GraphDataStream(null, GraphType.LINE, 0, 1, dataStream2, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream,
                graphDataStream2
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(3));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(2, bb.getInt());
        assertEquals(1.11D, bb.getDouble(), 0.1);
        assertEquals(1111111, bb.getLong());
        assertEquals(1.22D, bb.getDouble(), 0.1);
        assertEquals(2222222, bb.getLong());
    }

    @Test
    public void testGetGraphDataForTagAndForEnhancedGraphMAX() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        Tag tag0 = new Tag(100_000, "Tag1", new int[] {0,1});

        clientPair.appClient.createTag(1, tag0);
        String createdTag = clientPair.appClient.getBody(2);
        Tag tag = JsonParser.parseTag(createdTag, 0);
        assertNotNull(tag);
        assertEquals(100_000, tag.id);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createTag(2, tag)));


        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath2, 1.112D, 1111111);
        FileUtils.write(pinReportingDataPath2, 1.222D, 2222222);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 100_000, dataStream, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(3));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(2, bb.getInt());
        assertEquals(1.112D, bb.getDouble(), 0.1);
        assertEquals(1111111, bb.getLong());
        assertEquals(1.222D, bb.getDouble(), 0.1);
        assertEquals(2222222, bb.getLong());
    }

    @Test
    public void testGetGraphDataForTagAndForEnhancedGraphMIN() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        Tag tag0 = new Tag(100_000, "Tag1", new int[] {0,1});

        clientPair.appClient.createTag(1, tag0);
        String createdTag = clientPair.appClient.getBody(2);
        Tag tag = JsonParser.parseTag(createdTag, 0);
        assertNotNull(tag);
        assertEquals(100_000, tag.id);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createTag(2, tag)));


        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath2, 1.112D, 1111111);
        FileUtils.write(pinReportingDataPath2, 1.222D, 2222222);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 100_000, dataStream, AggregationFunctionType.MIN, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(3));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(2, bb.getInt());
        assertEquals(1.11D, bb.getDouble(), 0.1);
        assertEquals(1111111, bb.getLong());
        assertEquals(1.22D, bb.getDouble(), 0.1);
        assertEquals(2222222, bb.getLong());
    }

    @Test
    public void testGetGraphDataForTagAndForEnhancedGraphSUM() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        Tag tag0 = new Tag(100_000, "Tag1", new int[] {0,1});

        clientPair.appClient.createTag(1, tag0);
        String createdTag = clientPair.appClient.getBody(2);
        Tag tag = JsonParser.parseTag(createdTag, 0);
        assertNotNull(tag);
        assertEquals(100_000, tag.id);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createTag(2, tag)));


        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath2, 1.112D, 1111111);
        FileUtils.write(pinReportingDataPath2, 1.222D, 2222222);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 100_000, dataStream, AggregationFunctionType.SUM, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(3));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(2, bb.getInt());
        assertEquals(2.222D, bb.getDouble(), 0.001);
        assertEquals(1111111, bb.getLong());
        assertEquals(2.442D, bb.getDouble(), 0.001);
        assertEquals(2222222, bb.getLong());
    }

    @Test
    public void testGetGraphDataForTagAndForEnhancedGraphAVG() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        Tag tag0 = new Tag(100_000, "Tag1", new int[] {0,1});

        clientPair.appClient.createTag(1, tag0);
        String createdTag = clientPair.appClient.getBody(2);
        Tag tag = JsonParser.parseTag(createdTag, 0);
        assertNotNull(tag);
        assertEquals(100_000, tag.id);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createTag(2, tag)));


        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath2, 1.112D, 1111111);
        FileUtils.write(pinReportingDataPath2, 1.222D, 2222222);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 100_000, dataStream, AggregationFunctionType.AVG, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(3));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(2, bb.getInt());
        assertEquals(1.111D, bb.getDouble(), 0.001);
        assertEquals(1111111, bb.getLong());
        assertEquals(1.221D, bb.getDouble(), 0.001);
        assertEquals(2222222, bb.getLong());
    }

    @Test
    public void testGetGraphDataForTagAndForEnhancedGraphMEDIAN() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        Tag tag0 = new Tag(100_000, "Tag1", new int[] {0,1});

        clientPair.appClient.createTag(1, tag0);
        String createdTag = clientPair.appClient.getBody(2);
        Tag tag = JsonParser.parseTag(createdTag, 0);
        assertNotNull(tag);
        assertEquals(100_000, tag.id);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createTag(2, tag)));


        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath2, 1.112D, 1111111);
        FileUtils.write(pinReportingDataPath2, 1.222D, 2222222);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 100_000, dataStream, AggregationFunctionType.MED, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(3));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(2, bb.getInt());
        assertEquals(1.112D, bb.getDouble(), 0.001);
        assertEquals(1111111, bb.getLong());
        assertEquals(1.222D, bb.getDouble(), 0.001);
        assertEquals(2222222, bb.getLong());
    }

    @Test
    public void testGetGraphDataForTagAndForEnhancedGraphMEDIANFor3Devices() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);
        Device device2 = new Device(2, "My Device", BoardType.ESP8266);

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.createDevice(1, device2);
        device = clientPair.appClient.parseDevice(2);
        assertNotNull(device);
        assertNotNull(device.token);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createDevice(2, device)));

        Tag tag0 = new Tag(100_000, "Tag1", new int[] {0, 1, 2});

        clientPair.appClient.createTag(1, tag0);
        String createdTag = clientPair.appClient.getBody(3);
        Tag tag = JsonParser.parseTag(createdTag, 0);
        assertNotNull(tag);
        assertEquals(100_000, tag.id);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createTag(3, tag)));


        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath2, 1.112D, 1111111);
        FileUtils.write(pinReportingDataPath2, 1.222D, 2222222);

        Path pinReportingDataPath3 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 2, PinType.VIRTUAL, (byte) 88, GraphPeriod.DAY.granularityType));
        FileUtils.write(pinReportingDataPath3, 1.113D, 1111111);
        FileUtils.write(pinReportingDataPath3, 1.223D, 2222222);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 100_000, dataStream, AggregationFunctionType.MED, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(4));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(2, bb.getInt());
        assertEquals(1.112D, bb.getDouble(), 0.001);
        assertEquals(1111111, bb.getLong());
        assertEquals(1.222D, bb.getDouble(), 0.001);
        assertEquals(2222222, bb.getLong());
    }

    @Test
    public void testGetGraphDataForEnhancedGraphWithEmptyDataStream() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphGranularityType.HOURLY));

        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, null, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);

        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(2, NO_DATA)));
    }

    @Test
    public void testGetGraphDataForEnhancedGraph() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphPeriod.ONE_HOUR.granularityType));

        for (int point = 0; point < GraphPeriod.ONE_HOUR.numberOfPoints + 1; point++) {
            FileUtils.write(pinReportingDataPath, (double) point, 1111111 + point);
        }

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 8, PinType.DIGITAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.ONE_HOUR);

        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(60, bb.getInt());
        for (int point = 1; point < GraphPeriod.ONE_HOUR.numberOfPoints + 1; point++) {
            assertEquals(point, bb.getDouble(), 0.1);
            assertEquals(1111111 + point , bb.getLong());
        }
    }

    @Test
    public void testGetGraphDataForEnhancedGraphFor2Streams() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphPeriod.DAY.granularityType));

        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 8, PinType.DIGITAL);
        DataStream dataStream2 = new DataStream((byte) 9, PinType.DIGITAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        GraphDataStream graphDataStream2 = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream2, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream,
                graphDataStream2
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(2, bb.getInt());
        assertEquals(1.11D, bb.getDouble(), 0.1);
        assertEquals(1111111, bb.getLong());
        assertEquals(1.22D, bb.getDouble(), 0.1);
        assertEquals(2222222, bb.getLong());
        assertEquals(0, bb.getInt());
    }

    @Test
    public void testGetGraphDataForEnhancedGraphWithWrongDataStream() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphGranularityType.HOURLY));

        FileUtils.write(pinReportingDataPath, 1.11D, 1111111);
        FileUtils.write(pinReportingDataPath, 1.22D, 2222222);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 8, null);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, NO_DATA)));
    }

    @Test
    public void testGetLIVEGraphDataForEnhancedGraph() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }


        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.LIVE);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, NO_DATA)));

        clientPair.hardwareClient.send("hardware vw 88 111");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(1, b("1-0 vw 88 111"))));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.LIVE);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(1, bb.getInt());
        assertEquals(111D, bb.getDouble(), 0.1);
        assertEquals(System.currentTimeMillis(), bb.getLong(), 2000);

        for (int i = 1; i <= 60; i++) {
            clientPair.hardwareClient.send("hardware vw 88 " + i);
        }

        verify(clientPair.appClient.responseMock, timeout(10000)).channelRead(any(), eq(new HardwareMessage(61, b("1-0 vw 88 60"))));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.LIVE);
        graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(60, bb.getInt());
        for (int i = 1; i <= 60; i++) {
            assertEquals(i, bb.getDouble(), 0.1);
            assertEquals(System.currentTimeMillis(), bb.getLong(), 10000);
        }
    }

    @Test
    public void testNoLiveDataWhenNoGraph() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        clientPair.hardwareClient.send("hardware vw 88 111");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(1, b("1-0 vw 88 111"))));

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.LIVE);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(2, NO_DATA)));
    }

    @Test
    public void testNoLiveDataWhenNoGraph2() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        clientPair.hardwareClient.send("hardware vw 88 111");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(1, b("1-0 vw 88 111"))));

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.LIVE);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(2, NO_DATA)));

        clientPair.hardwareClient.send("hardware vw 88 111");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(2, b("1-0 vw 88 111"))));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.LIVE);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(1, bb.getInt());
        assertEquals(111D, bb.getDouble(), 0.1);
        assertEquals(System.currentTimeMillis(), bb.getLong(), 2000);

        clientPair.appClient.deleteWidget(1, 432);
        clientPair.appClient.verifyResult(ok(2));

        clientPair.hardwareClient.send("hardware vw 88 111");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(3, b("1-0 vw 88 111"))));

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(3));

        clientPair.appClient.send("getenhanceddata 1" + b(" 432 LIVE"));
        clientPair.appClient.reset();
        graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(1, bb.getInt());
        assertEquals(111D, bb.getDouble(), 0.1);
        assertEquals(System.currentTimeMillis(), bb.getLong(), 2000);
    }

    @Test
    public void testGetLIVEGraphDataForEnhancedGraphWithPaging() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }


        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 88, PinType.VIRTUAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.LIVE);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, NO_DATA)));

        clientPair.hardwareClient.send("hardware vw 88 111");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(1, b("1-0 vw 88 111"))));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.LIVE);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(1, bb.getInt());
        assertEquals(111D, bb.getDouble(), 0.1);
        assertEquals(System.currentTimeMillis(), bb.getLong(), 2000);

        for (int i = 1; i <= 60; i++) {
            clientPair.hardwareClient.send("hardware vw 88 " + i);
        }

        verify(clientPair.appClient.responseMock, timeout(10000)).channelRead(any(), eq(new HardwareMessage(61, b("1-0 vw 88 60"))));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.LIVE);
        graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        bb = ByteBuffer.wrap(decompressedGraphData);

        assertEquals(1, bb.getInt());
        assertEquals(60, bb.getInt());
        for (int i = 1; i <= 60; i++) {
            assertEquals(i, bb.getDouble(), 0.1);
            assertEquals(System.currentTimeMillis(), bb.getLong(), 10000);
        }
    }


    @Test
    public void testPagingWorksForGetEnhancedHistoryDataPartialData() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphGranularityType.MINUTE));

        try (DataOutputStream dos = new DataOutputStream(
                    Files.newOutputStream(pinReportingDataPath, CREATE, APPEND))) {
            for (int i = 1; i <= 61; i++) {
                dos.writeDouble(i);
                dos.writeLong(i * 1000);
            }
            dos.flush();
        }

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 8, PinType.DIGITAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.ONE_HOUR, 1);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);


        assertEquals(1, bb.getInt());
        assertEquals(1, bb.getInt());
        assertEquals(1D, bb.getDouble(), 0.1);
        assertEquals(1000, bb.getLong());
    }

    @Test
    public void testPagingWorksForGetEnhancedHistoryDataFullData() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphGranularityType.MINUTE));

        try (DataOutputStream dos = new DataOutputStream(
                Files.newOutputStream(pinReportingDataPath, CREATE, APPEND))) {
            for (int i = 1; i <= 120; i++) {
                dos.writeDouble(i);
                dos.writeLong(i * 1000);
            }
            dos.flush();
        }

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 8, PinType.DIGITAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.ONE_HOUR, 1);
        BinaryMessage graphDataResponse = clientPair.appClient.getBinaryBody();

        assertNotNull(graphDataResponse);
        byte[] decompressedGraphData = BaseTest.decompress(graphDataResponse.getBytes());
        ByteBuffer bb = ByteBuffer.wrap(decompressedGraphData);


        assertEquals(1, bb.getInt());
        assertEquals(60, bb.getInt());
        for (int i = 1; i <= 60; i++) {
            assertEquals(i, bb.getDouble(), 0.1);
            assertEquals(i * 1000, bb.getLong());
        }
    }

    @Test
    public void testPagingWorksForGetEnhancedHistoryDataFullDataAndSecondPage() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphGranularityType.MINUTE));

        try (DataOutputStream dos = new DataOutputStream(
                Files.newOutputStream(pinReportingDataPath, CREATE, APPEND))) {
            for (int i = 1; i <= 120; i++) {
                dos.writeDouble(i);
                dos.writeLong(i * 1000);
            }
            dos.flush();
        }

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 8, PinType.DIGITAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.ONE_HOUR, 5);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, NO_DATA)));
    }

    @Test
    public void testPagingWorksForGetEnhancedHistoryDataWhenNoData() throws Exception {
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        Path pinReportingDataPath = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphGranularityType.MINUTE));

        try (DataOutputStream dos = new DataOutputStream(
                Files.newOutputStream(pinReportingDataPath, CREATE, APPEND))) {
            for (int i = 1; i <= 60; i++) {
                dos.writeDouble(i);
                dos.writeLong(i * 1000);
            }
            dos.flush();
        }

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 8, PinType.DIGITAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.ONE_HOUR, 1);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, NO_DATA)));
    }

    @Test
    public void testDeleteWorksForEnhancedGraph() throws Exception {
        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 8, PinType.DIGITAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.send("deleteEnhancedData 1\0" + "432");
        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(ok(2)));
        clientPair.appClient.reset();

        clientPair.appClient.getEnhancedGraphData(1, 432, GraphPeriod.DAY);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, NO_DATA)));
    }

    private static String getFileNameByMask(String pattern) {
        File dir = new File(blynkTempDir);
        File[] files = dir.listFiles((dir1, name) -> name.startsWith(pattern));
        return latest(files).getName();
    }

    private static File latest(File[] files) {
        long lastMod = Long.MIN_VALUE;
        File choice = null;
        for (File file : files) {
            if (file.lastModified() > lastMod) {
                choice = file;
                lastMod = file.lastModified();
            }
        }
        return choice;
    }

    @Test
    public void testExportDataFromHistoryGraph() throws Exception {
        clientPair.appClient.send("export 1");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(illegalCommand(1)));

        clientPair.appClient.send("export 1 666");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(illegalCommand(2)));

        clientPair.appClient.send("export 1 1");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(illegalCommand(3)));

        clientPair.appClient.send("export 1 14");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(4, NO_DATA)));

        //generate fake reporting data
        Path userReportDirectory = Paths.get(holder.props.getProperty("data.folder"), "data", getUserName());
        Files.createDirectories(userReportDirectory);
        Path userReportFile = Paths.get(userReportDirectory.toString(),
                ReportingDiskDao.generateFilename(1, 0, PinType.ANALOG, (byte) 7, GraphGranularityType.MINUTE));
        FileUtils.write(userReportFile, 1.1, 1L);
        FileUtils.write(userReportFile, 2.2, 2L);

        clientPair.appClient.send("export 1 14");
        verify(holder.mailWrapper, timeout(1000)).sendHtml(eq(getUserName()), eq("History graph data for project My Dashboard"), contains("/" + getUserName() + "_1_0_a7_"));
    }

    @Test
    public void testGeneratedCSVIsCorrect() throws Exception {
        //generate fake reporting data
        Path userReportDirectory = Paths.get(holder.props.getProperty("data.folder"), "data", getUserName());
        Files.createDirectories(userReportDirectory);
        String filename = ReportingDiskDao.generateFilename(1, 0, PinType.ANALOG, (byte) 7, GraphGranularityType.MINUTE);
        Path userReportFile = Paths.get(userReportDirectory.toString(), filename);
        FileUtils.write(userReportFile, 1.1, 1L);
        FileUtils.write(userReportFile, 2.2, 2L);

        clientPair.appClient.send("export 1 14");
        clientPair.appClient.verifyResult(ok(1));

        String csvFileName = getFileNameByMask(getUserName() + "_1_0_a7_");
        verify(holder.mailWrapper, timeout(1000)).sendHtml(eq(getUserName()), eq("History graph data for project My Dashboard"), contains(csvFileName));

        try (InputStream fileStream = new FileInputStream(Paths.get(blynkTempDir, csvFileName).toString());
             InputStream gzipStream = new GZIPInputStream(fileStream);
             BufferedReader buffered = new BufferedReader(new InputStreamReader(gzipStream))) {

            String[] lineSplit = buffered.readLine().split(",");
            assertEquals(1.1D, Double.parseDouble(lineSplit[0]), 0.001D);
            assertEquals(1, Long.parseLong(lineSplit[1]));
            assertEquals(0, Long.parseLong(lineSplit[2]));

            lineSplit = buffered.readLine().split(",");
            assertEquals(2.2D, Double.parseDouble(lineSplit[0]), 0.001D);
            assertEquals(2, Long.parseLong(lineSplit[1]));
            assertEquals(0, Long.parseLong(lineSplit[2]));
        }
    }

    @Test
    public void testGeneratedCSVIsCorrectForMultiDevicesNoData() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);
        device1.status = Status.OFFLINE;

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.createWidget(1, "{\"id\":200000, \"deviceIds\":[0,1], \"width\":1, \"height\":1, \"x\":0, \"y\":0, \"label\":\"Some Text\", \"type\":\"DEVICE_SELECTOR\"}");
        clientPair.appClient.verifyResult(ok(2));

        clientPair.appClient.reset();

        clientPair.appClient.send("export 1-200000 14");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, NO_DATA)));
    }

    @Test
    public void cleanNotUsedPinDataWorksAsExpected() throws Exception {
        HistoryGraphUnusedPinDataCleanerWorker cleaner = new HistoryGraphUnusedPinDataCleanerWorker(holder.userDao, holder.reportingDiskDao);
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        //this file has corresponding history graph
        Path pinReportingDataPath1 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.ANALOG, (byte) 7, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath1, 1.11D, 1111111);

        //those are not
        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 100, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath2, 1.11D, 1111111);

        Path pinReportingDataPath3 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 101, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath3, 1.11D, 1111111);

        Path pinReportingDataPath4 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 102, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath4, 1.11D, 1111111);

        assertTrue(Files.exists(pinReportingDataPath1));
        assertTrue(Files.exists(pinReportingDataPath2));
        assertTrue(Files.exists(pinReportingDataPath3));
        assertTrue(Files.exists(pinReportingDataPath4));

        //creating widget just to make user profile "updated"
        clientPair.appClient.createWidget(1, "{\"id\":200000, \"deviceIds\":[0,1], \"width\":1, \"height\":1, \"x\":0, \"y\":0, \"label\":\"Some Text\", \"type\":\"DEVICE_SELECTOR\"}");
        clientPair.appClient.verifyResult(ok(1));

        cleaner.run();

        assertTrue(Files.exists(pinReportingDataPath1));
        assertTrue(Files.notExists(pinReportingDataPath2));
        assertTrue(Files.notExists(pinReportingDataPath3));
        assertTrue(Files.notExists(pinReportingDataPath4));
    }

    @Test
    public void cleanNotUsedPinDataWorksAsExpectedForSuperChart() throws Exception {
        HistoryGraphUnusedPinDataCleanerWorker cleaner = new HistoryGraphUnusedPinDataCleanerWorker(holder.userDao, holder.reportingDiskDao);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream1 = new DataStream((byte) 8, PinType.DIGITAL);
        DataStream dataStream2 = new DataStream((byte) 9, PinType.DIGITAL);
        DataStream dataStream3 = new DataStream((byte) 10, PinType.DIGITAL);
        GraphDataStream graphDataStream1 = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream1, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        GraphDataStream graphDataStream2 = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream2, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        GraphDataStream graphDataStream3 = new GraphDataStream(null, GraphType.LINE, 0, 0, dataStream3, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream1,
                graphDataStream2,
                graphDataStream3,
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(1));

        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        //this file has corresponding history graph
        Path pinReportingDataPath1 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath1, 1.11D, 1111111);

        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 9, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath2, 1.11D, 1111111);

        Path pinReportingDataPath3 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 10, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath3, 1.11D, 1111111);

        //those are not
        Path pinReportingDataPath4 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 11, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath4, 1.11D, 1111111);

        assertTrue(Files.exists(pinReportingDataPath1));
        assertTrue(Files.exists(pinReportingDataPath2));
        assertTrue(Files.exists(pinReportingDataPath3));
        assertTrue(Files.exists(pinReportingDataPath4));

        cleaner.run();

        assertTrue(Files.exists(pinReportingDataPath1));
        assertTrue(Files.exists(pinReportingDataPath2));
        assertTrue(Files.exists(pinReportingDataPath3));
        assertTrue(Files.notExists(pinReportingDataPath4));
    }

    @Test
    public void cleanNotUsedPinDataWorksAsExpectedForSuperChartInDeviceTiles() throws Exception {
        HistoryGraphUnusedPinDataCleanerWorker cleaner = new HistoryGraphUnusedPinDataCleanerWorker(holder.userDao, holder.reportingDiskDao);

        DeviceTiles deviceTiles = new DeviceTiles();
        deviceTiles.id = 21321;
        deviceTiles.x = 8;
        deviceTiles.y = 8;
        deviceTiles.width = 50;
        deviceTiles.height = 100;

        clientPair.appClient.createWidget(1, deviceTiles);
        clientPair.appClient.verifyResult(ok(1));

        int[] deviceIds = new int[] {1};

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream1 = new DataStream((byte) 8, PinType.DIGITAL);
        DataStream dataStream2 = new DataStream((byte) 9, PinType.DIGITAL);
        GraphDataStream graphDataStream1 = new GraphDataStream(null, GraphType.LINE, 0, -1, dataStream1, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        GraphDataStream graphDataStream2 = new GraphDataStream(null, GraphType.LINE, 0, -1, dataStream2, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream1,
                graphDataStream2,
        };
        PageTileTemplate tileTemplate = new PageTileTemplate(1,
                new Widget[] {
                        enhancedHistoryGraph
                },
                deviceIds, "123", "name", "iconName", BoardType.ESP8266, new DataStream((byte) 1, PinType.VIRTUAL),
                false, null, null, null, 0, 0, FontSize.LARGE, false, 2);

        clientPair.appClient.send("createTemplate " + b("1 " + deviceTiles.id + " ")
                + MAPPER.writeValueAsString(tileTemplate));
        clientPair.appClient.verifyResult(ok(2));

        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        //this file has corresponding history graph
        Path pinReportingDataPath1 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.DIGITAL, (byte) 8, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath1, 1.11D, 1111111);

        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.DIGITAL, (byte) 9, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath2, 1.11D, 1111111);

        //those are not
        Path pinReportingDataPath3 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.DIGITAL, (byte) 10, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath3, 1.11D, 1111111);

        Path pinReportingDataPath4 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.DIGITAL, (byte) 11, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath4, 1.11D, 1111111);

        assertTrue(Files.exists(pinReportingDataPath1));
        assertTrue(Files.exists(pinReportingDataPath2));
        assertTrue(Files.exists(pinReportingDataPath3));
        assertTrue(Files.exists(pinReportingDataPath4));

        cleaner.run();

        assertTrue(Files.exists(pinReportingDataPath1));
        assertTrue(Files.exists(pinReportingDataPath2));
        assertTrue(Files.notExists(pinReportingDataPath3));
        assertTrue(Files.notExists(pinReportingDataPath4));
    }

    @Test
    public void cleanNotUsedPinDataWorksAsExpectedForSuperChartWithDeviceSelector() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);
        device1.status = Status.OFFLINE;

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.createWidget(1, "{\"id\":200000, \"deviceIds\":[0,1], \"width\":1, \"height\":1, \"value\":0, \"x\":0, \"y\":0, \"label\":\"Some Text\", \"type\":\"DEVICE_SELECTOR\"}");
        clientPair.appClient.verifyResult(ok(2));

        HistoryGraphUnusedPinDataCleanerWorker cleaner = new HistoryGraphUnusedPinDataCleanerWorker(holder.userDao, holder.reportingDiskDao);

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream1 = new DataStream((byte) 8, PinType.DIGITAL);
        DataStream dataStream2 = new DataStream((byte) 9, PinType.DIGITAL);
        DataStream dataStream3 = new DataStream((byte) 10, PinType.DIGITAL);
        GraphDataStream graphDataStream1 = new GraphDataStream(null, GraphType.LINE, 0, 200000, dataStream1, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        GraphDataStream graphDataStream2 = new GraphDataStream(null, GraphType.LINE, 0, 200000, dataStream2, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        GraphDataStream graphDataStream3 = new GraphDataStream(null, GraphType.LINE, 0, 200000, dataStream3, AggregationFunctionType.MAX, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream1,
                graphDataStream2,
                graphDataStream3,
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(3));

        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        //this file has corresponding history graph
        Path pinReportingDataPath10 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath10, 1.11D, 1111111);

        Path pinReportingDataPath20 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 9, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath20, 1.11D, 1111111);

        Path pinReportingDataPath30 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 10, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath30, 1.11D, 1111111);

        Path pinReportingDataPath11 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.DIGITAL, (byte) 8, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath11, 1.11D, 1111111);

        Path pinReportingDataPath21 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.DIGITAL, (byte) 9, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath21, 1.11D, 1111111);

        Path pinReportingDataPath31 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.DIGITAL, (byte) 10, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath31, 1.11D, 1111111);

        //those are not
        Path pinReportingDataPath40 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 11, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath40, 1.11D, 1111111);

        Path pinReportingDataPath41 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 1, PinType.DIGITAL, (byte) 11, GraphGranularityType.HOURLY));
        FileUtils.write(pinReportingDataPath41, 1.11D, 1111111);

        //3 files for device 0
        assertTrue(Files.exists(pinReportingDataPath10));
        assertTrue(Files.exists(pinReportingDataPath20));
        assertTrue(Files.exists(pinReportingDataPath30));

        //3 files for device 1
        assertTrue(Files.exists(pinReportingDataPath11));
        assertTrue(Files.exists(pinReportingDataPath21));
        assertTrue(Files.exists(pinReportingDataPath31));

        assertTrue(Files.exists(pinReportingDataPath40));
        assertTrue(Files.exists(pinReportingDataPath41));

        cleaner.run();

        //3 files for device 0
        assertTrue(Files.exists(pinReportingDataPath10));
        assertTrue(Files.exists(pinReportingDataPath20));
        assertTrue(Files.exists(pinReportingDataPath30));

        //3 files for device 1
        assertTrue(Files.exists(pinReportingDataPath11));
        assertTrue(Files.exists(pinReportingDataPath21));
        assertTrue(Files.exists(pinReportingDataPath31));

        assertTrue(Files.notExists(pinReportingDataPath40));
        assertTrue(Files.notExists(pinReportingDataPath41));
    }

    @Test
    public void truncateReportingDataWorks() throws Exception {
        ReportingTruncateWorker truncateWorker = new ReportingTruncateWorker(holder.reportingDiskDao);
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        //this file has corresponding history graph
        Path pinReportingDataPath1 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.ANALOG, (byte) 7, GraphGranularityType.MINUTE));

        Path pinReportingDataPath2 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.VIRTUAL, (byte) 7, GraphGranularityType.MINUTE));
        FileUtils.write(pinReportingDataPath2, 1.11D, 1);

        Path pinReportingDataPath3 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.VIRTUAL, (byte) 7, GraphGranularityType.HOURLY));

        //write max amount of data for 1 week + 1 point
        for (int i = 0; i < 30 * 24 * 60 + 1; i++) {
            FileUtils.write(pinReportingDataPath1, 1.11D, i);
            FileUtils.write(pinReportingDataPath3, 1.11D, i);
        }

        assertEquals((30 * 24 * 60 + 1) * ReportingUtil.REPORTING_RECORD_SIZE, Files.size(pinReportingDataPath1));
        assertEquals(16, Files.size(pinReportingDataPath2));
        assertEquals((30 * 24 * 60 + 1) * ReportingUtil.REPORTING_RECORD_SIZE, Files.size(pinReportingDataPath3));
        truncateWorker.run();

        //expecting truncated file here
        assertEquals(30 * 24 * 60 * ReportingUtil.REPORTING_RECORD_SIZE, Files.size(pinReportingDataPath1));
        assertEquals(16, Files.size(pinReportingDataPath2));
        assertEquals((30 * 24 * 60 + 1) * ReportingUtil.REPORTING_RECORD_SIZE, Files.size(pinReportingDataPath3));

        //check truncate is correct
        ByteBuffer bb = FileUtils.read(pinReportingDataPath1, 30 * 24 * 60);

        for (int i = 1; i < 30 * 24 * 60 + 1; i++) {
            assertEquals(1.11D, bb.getDouble(), 0.001D);
            assertEquals(i, bb.getLong());
        }

        bb = FileUtils.read(pinReportingDataPath3, 30 * 24 * 60 + 1);
        for (int i = 0; i < 30 * 24 * 60 + 1; i++) {
            assertEquals(1.11D, bb.getDouble(), 0.001D);
            assertEquals(i, bb.getLong());
        }
    }

    @Test
    public void deleteOldExportFiles() throws Exception {
        Path csvDir = Paths.get(FileUtils.CSV_DIR);
        if (Files.notExists(csvDir)) {
            Files.createDirectories(csvDir);
        }

        //this file has corresponding history graph
        Path csvFile = Paths.get(csvDir.toString(), "123.csv.gz");

        FileUtils.write(csvFile, 1.11D, 1);
        assertTrue(Files.exists(csvFile));

        ReportingTruncateWorker truncateWorker = new ReportingTruncateWorker(holder.reportingDiskDao, 0, 0);
        truncateWorker.run();
        assertTrue(Files.notExists(csvFile));
    }

    @Test
    public void doNotTruncateFileWithCorrectSize() throws Exception {
        ReportingTruncateWorker truncateWorker = new ReportingTruncateWorker(holder.reportingDiskDao);
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());
        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }

        //this file has corresponding history graph
        Path pinReportingDataPath1 = Paths.get(tempDir, "data", getUserName(),
                ReportingDiskDao.generateFilename(1, 0, PinType.ANALOG, (byte) 7, GraphGranularityType.MINUTE));

        //write max amount of data for 1 week + 1 point
        for (int i = 0; i < 7 * 24 * 60; i++) {
            FileUtils.write(pinReportingDataPath1, 1.11D, i);
        }

        assertEquals((7 * 24 * 60) * ReportingUtil.REPORTING_RECORD_SIZE, Files.size(pinReportingDataPath1));
        truncateWorker.run();

        //expecting truncated file here
        assertEquals(7 * 24 * 60 * ReportingUtil.REPORTING_RECORD_SIZE, Files.size(pinReportingDataPath1));

        //check no truncate
        ByteBuffer bb = FileUtils.read(pinReportingDataPath1, 7 * 24 * 60);

        for (int i = 0; i < 7 * 24 * 60; i++) {
            assertEquals(1.11D, bb.getDouble(), 0.001D);
            assertEquals(i, bb.getLong());
        }

        assertTrue(Files.exists(userReportFolder));
    }

    @Test
    public void truncateReportingDataDontFailsInEmptyFolder() throws Exception {
        ReportingTruncateWorker truncateWorker = new ReportingTruncateWorker(holder.reportingDiskDao);
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());

        truncateWorker.run();

        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }
        truncateWorker.run();
    }

    @Test
    public void truncateReportingDataDeletesEmptyFolder() throws Exception {
        ReportingTruncateWorker truncateWorker = new ReportingTruncateWorker(holder.reportingDiskDao);
        String tempDir = holder.props.getProperty("data.folder");

        Path userReportFolder = Paths.get(tempDir, "data", getUserName());

        if (Files.notExists(userReportFolder)) {
            Files.createDirectories(userReportFolder);
        }
        truncateWorker.run();

        assertTrue(Files.notExists(userReportFolder));
    }

    @Test
    public void testGeneratedCSVIsCorrectForMultiDevicesAndEnhancedGraph() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);
        device1.status = Status.OFFLINE;

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.createWidget(1, "{\"id\":200000, \"deviceIds\":[0,1], \"width\":1, \"height\":1, \"x\":0, \"y\":0, \"label\":\"Some Text\", \"type\":\"DEVICE_SELECTOR\"}");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(ok(2)));

        EnhancedHistoryGraph enhancedHistoryGraph = new EnhancedHistoryGraph();
        enhancedHistoryGraph.id = 432;
        enhancedHistoryGraph.width = 8;
        enhancedHistoryGraph.height = 4;
        DataStream dataStream = new DataStream((byte) 8, PinType.DIGITAL);
        GraphDataStream graphDataStream = new GraphDataStream(null, GraphType.LINE, 0, 200_000, dataStream, null, 0, null, null, null, 0, 0, false, null, false, false, false, null, 0, false, 0);
        enhancedHistoryGraph.dataStreams = new GraphDataStream[] {
                graphDataStream
        };

        clientPair.appClient.createWidget(1, enhancedHistoryGraph);
        clientPair.appClient.verifyResult(ok(3));

        clientPair.appClient.reset();

        //generate fake reporting data
        Path userReportDirectory = Paths.get(holder.props.getProperty("data.folder"), "data", getUserName());
        Files.createDirectories(userReportDirectory);

        String filename = ReportingDiskDao.generateFilename(1, 0, PinType.DIGITAL, (byte) 8, GraphGranularityType.MINUTE);
        Path userReportFile = Paths.get(userReportDirectory.toString(), filename);
        FileUtils.write(userReportFile, 1.1, 1L);
        FileUtils.write(userReportFile, 2.2, 2L);

        filename = ReportingDiskDao.generateFilename(1, 1, PinType.DIGITAL, (byte) 8, GraphGranularityType.MINUTE);
        userReportFile = Paths.get(userReportDirectory.toString(), filename);
        FileUtils.write(userReportFile, 11.1, 11L);
        FileUtils.write(userReportFile, 12.2, 12L);

        clientPair.appClient.send("export 1 432");
        clientPair.appClient.verifyResult(ok(1));

        String csvFileName = getFileNameByMask(getUserName() + "_1_200000_d8_");
        verify(holder.mailWrapper, timeout(1000)).sendHtml(eq(getUserName()), eq("History graph data for project My Dashboard"), contains(csvFileName));

        try (InputStream fileStream = new FileInputStream(Paths.get(blynkTempDir, csvFileName).toString());
             InputStream gzipStream = new GZIPInputStream(fileStream);
             BufferedReader buffered = new BufferedReader(new InputStreamReader(gzipStream))) {

            //first device
            String[] lineSplit = buffered.readLine().split(",");
            assertEquals(1.1D, Double.parseDouble(lineSplit[0]), 0.001D);
            assertEquals(1, Long.parseLong(lineSplit[1]));
            assertEquals(0, Long.parseLong(lineSplit[2]));

            lineSplit = buffered.readLine().split(",");
            assertEquals(2.2D, Double.parseDouble(lineSplit[0]), 0.001D);
            assertEquals(2, Long.parseLong(lineSplit[1]));
            assertEquals(0, Long.parseLong(lineSplit[2]));

            //second device
            lineSplit = buffered.readLine().split(",");
            assertEquals(11.1D, Double.parseDouble(lineSplit[0]), 0.001D);
            assertEquals(11, Long.parseLong(lineSplit[1]));
            assertEquals(1, Long.parseLong(lineSplit[2]));

            lineSplit = buffered.readLine().split(",");
            assertEquals(12.2D, Double.parseDouble(lineSplit[0]), 0.001D);
            assertEquals(12, Long.parseLong(lineSplit[1]));
            assertEquals(1, Long.parseLong(lineSplit[2]));
        }
    }

    @Test
    public void testGeneratedCSVIsCorrectForMultiDevices() throws Exception {
        Device device1 = new Device(1, "My Device", BoardType.ESP8266);
        device1.status = Status.OFFLINE;

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.parseDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.createWidget(1, "{\"id\":200000, \"deviceIds\":[0,1], \"width\":1, \"height\":1, \"x\":0, \"y\":0, \"label\":\"Some Text\", \"type\":\"DEVICE_SELECTOR\"}");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(ok(2)));

        clientPair.appClient.updateWidget(1, "{\n" +
                "                    \"type\":\"LOGGER\",\n" +
                "                    \"id\":14,\n" +
                "                    \"x\":0,\n" +
                "                    \"y\":6,\n" +
                "                    \"color\":0,\n" +
                "                    \"width\":8,\n" +
                "                    \"height\":3,\n" +
                "                    \"tabId\":0,\n" +
                "                    \"deviceId\":200000,\n" +
                "                    \"pins\":\n" +
                "                        [\n" +
                "                            {\"pinType\":\"ANALOG\", \"pin\":7,\"pwmMode\":false,\"rangeMappingOn\":false,\"min\":0,\"max\":255},\n" +
                "                            {\"pin\":-1,\"pwmMode\":false,\"rangeMappingOn\":false,\"min\":0,\"max\":0},\n" +
                "                            {\"pin\":-1,\"pwmMode\":false,\"rangeMappingOn\":false,\"min\":0,\"max\":0},\n" +
                "                            {\"pin\":-1,\"pwmMode\":false,\"rangeMappingOn\":false,\"min\":0,\"max\":0}\n" +
                "                        ],\n" +
                "                    \"period\":\"THREE_MONTHS\",\n" +
                "                    \"showLegends\":true\n" +
                "                }");

        clientPair.appClient.reset();

        //generate fake reporting data
        Path userReportDirectory = Paths.get(holder.props.getProperty("data.folder"), "data", getUserName());
        Files.createDirectories(userReportDirectory);

        String filename = ReportingDiskDao.generateFilename(1, 0, PinType.ANALOG, (byte) 7, GraphGranularityType.MINUTE);
        Path userReportFile = Paths.get(userReportDirectory.toString(), filename);
        FileUtils.write(userReportFile, 1.1, 1L);
        FileUtils.write(userReportFile, 2.2, 2L);

        filename = ReportingDiskDao.generateFilename(1, 1, PinType.ANALOG, (byte) 7, GraphGranularityType.MINUTE);
        userReportFile = Paths.get(userReportDirectory.toString(), filename);
        FileUtils.write(userReportFile, 11.1, 11L);
        FileUtils.write(userReportFile, 12.2, 12L);

        clientPair.appClient.send("export 1 14");
        clientPair.appClient.verifyResult(ok(1));

        String csvFileName = getFileNameByMask(getUserName() + "_1_200000_a7_");
        verify(holder.mailWrapper, timeout(1000)).sendHtml(eq(getUserName()), eq("History graph data for project My Dashboard"), contains(csvFileName));

        try (InputStream fileStream = new FileInputStream(Paths.get(blynkTempDir, csvFileName).toString());
             InputStream gzipStream = new GZIPInputStream(fileStream);
             BufferedReader buffered = new BufferedReader(new InputStreamReader(gzipStream))) {

            //first device
            String[] lineSplit = buffered.readLine().split(",");
            assertEquals(1.1D, Double.parseDouble(lineSplit[0]), 0.001D);
            assertEquals(1, Long.parseLong(lineSplit[1]));
            assertEquals(0, Long.parseLong(lineSplit[2]));

            lineSplit = buffered.readLine().split(",");
            assertEquals(2.2D, Double.parseDouble(lineSplit[0]), 0.001D);
            assertEquals(2, Long.parseLong(lineSplit[1]));
            assertEquals(0, Long.parseLong(lineSplit[2]));

            //second device
            lineSplit = buffered.readLine().split(",");
            assertEquals(11.1D, Double.parseDouble(lineSplit[0]), 0.001D);
            assertEquals(11, Long.parseLong(lineSplit[1]));
            assertEquals(1, Long.parseLong(lineSplit[2]));

            lineSplit = buffered.readLine().split(",");
            assertEquals(12.2D, Double.parseDouble(lineSplit[0]), 0.001D);
            assertEquals(12, Long.parseLong(lineSplit[1]));
            assertEquals(1, Long.parseLong(lineSplit[2]));
        }
    }
}
