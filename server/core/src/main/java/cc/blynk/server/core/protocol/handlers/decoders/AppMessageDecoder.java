package cc.blynk.server.core.protocol.handlers.decoders;

import cc.blynk.server.core.protocol.enums.Command;
import cc.blynk.server.core.protocol.model.messages.MessageBase;
import cc.blynk.server.core.protocol.model.messages.ResponseMessage;
import cc.blynk.server.core.stats.GlobalStats;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static cc.blynk.server.core.protocol.model.messages.MessageFactory.produce;

/**
 * Decodes input byte array into java message.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 18/1/2018.
 */
public class AppMessageDecoder extends ByteToMessageDecoder {

    private static final Logger log = LogManager.getLogger(AppMessageDecoder.class);

    private final GlobalStats stats;
    public static final int PROTOCOL_APP_HEADER_SIZE = 7;

    public AppMessageDecoder(GlobalStats stats) {
        this.stats = stats;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < PROTOCOL_APP_HEADER_SIZE) {
            return;
        }

        in.markReaderIndex();

        short command = in.readUnsignedByte();
        int messageId = in.readUnsignedShort();
        //actually here should be long. but we do not expect this number to be large
        //so it should perfectly fit int
        int codeOrLength = (int) in.readUnsignedInt();

        MessageBase message;
        if (command == Command.RESPONSE) {
            message = new ResponseMessage(messageId, codeOrLength);
        } else {
            if (in.readableBytes() < codeOrLength) {
                in.resetReaderIndex();
                return;
            }

            message = produce(messageId, command, (String) in.readCharSequence(codeOrLength, CharsetUtil.UTF_8));
        }

        log.trace("Incoming {}", message);

        stats.mark(command);

        out.add(message);
    }

}