package com.quokkajoa.pretask_realteeth.exception

sealed class JobException(message: String) : RuntimeException(message)

class JobNotFoundException(id: Long)
    : JobException("존재하지 않는 작업입니다. Job ID: $id")

class InvalidJobStateException(message: String)
    : JobException(message)

class JobStateInconsistencyException(message: String)
    : JobException(message)
