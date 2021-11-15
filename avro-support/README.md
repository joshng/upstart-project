# avro-codec

Contains utilities for working with the avro data with an integrated schema registry.

- `io.upstartproject.avrocodec.AvroCodec`: a utility for packing/unpacking content encoded as `PackedRecords`, using schemas registered in a configured `SchemaRepo`
- `io.upstartproject.avrocodec.MessageCodec`: a utility for packing/unpacking messages within `MessageEnvelopes`
- `io.upstartproject.avrocodec.SchemaRepo`: an interface representing a watchable persistent store for avro Schemas
- `io.upstartproject.avrocodec.kafka.KafkaSchemaRepo`: a `SchemaRepo` implementation which stores schemas in a kafka-topic
- `io.upstartproject.avro.MessageEnvelope`: a generic envelope for transmitting `PackedRecord` payloads with extensible context
- `io.upstartproject.avro.PackedRecord`: a generic structure for serializing avro-encoded messages along with their schema-identifier (`fingerprint`)
