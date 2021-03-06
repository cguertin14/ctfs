# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: morel.proto
"""Generated protocol buffer code."""
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()


from google.protobuf import empty_pb2 as google_dot_protobuf_dot_empty__pb2


DESCRIPTOR = _descriptor.FileDescriptor(
  name='morel.proto',
  package='',
  syntax='proto3',
  serialized_options=None,
  create_key=_descriptor._internal_create_key,
  serialized_pb=b'\n\x0bmorel.proto\x1a\x1bgoogle/protobuf/empty.proto\";\n\x14\x41uthenticationAnswer\x12\x15\n\rauthenticated\x18\x01 \x01(\x08\x12\x0c\n\x04\x66lag\x18\x02 \x01(\t2S\n\x05Morel\x12J\n\x17\x41uthenticationValidator\x12\x16.google.protobuf.Empty\x1a\x15.AuthenticationAnswer\"\x00\x62\x06proto3'
  ,
  dependencies=[google_dot_protobuf_dot_empty__pb2.DESCRIPTOR,])




_AUTHENTICATIONANSWER = _descriptor.Descriptor(
  name='AuthenticationAnswer',
  full_name='AuthenticationAnswer',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='authenticated', full_name='AuthenticationAnswer.authenticated', index=0,
      number=1, type=8, cpp_type=7, label=1,
      has_default_value=False, default_value=False,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='flag', full_name='AuthenticationAnswer.flag', index=1,
      number=2, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=44,
  serialized_end=103,
)

DESCRIPTOR.message_types_by_name['AuthenticationAnswer'] = _AUTHENTICATIONANSWER
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

AuthenticationAnswer = _reflection.GeneratedProtocolMessageType('AuthenticationAnswer', (_message.Message,), {
  'DESCRIPTOR' : _AUTHENTICATIONANSWER,
  '__module__' : 'morel_pb2'
  # @@protoc_insertion_point(class_scope:AuthenticationAnswer)
  })
_sym_db.RegisterMessage(AuthenticationAnswer)



_MOREL = _descriptor.ServiceDescriptor(
  name='Morel',
  full_name='Morel',
  file=DESCRIPTOR,
  index=0,
  serialized_options=None,
  create_key=_descriptor._internal_create_key,
  serialized_start=105,
  serialized_end=188,
  methods=[
  _descriptor.MethodDescriptor(
    name='AuthenticationValidator',
    full_name='Morel.AuthenticationValidator',
    index=0,
    containing_service=None,
    input_type=google_dot_protobuf_dot_empty__pb2._EMPTY,
    output_type=_AUTHENTICATIONANSWER,
    serialized_options=None,
    create_key=_descriptor._internal_create_key,
  ),
])
_sym_db.RegisterServiceDescriptor(_MOREL)

DESCRIPTOR.services_by_name['Morel'] = _MOREL

# @@protoc_insertion_point(module_scope)
