/** Shared web building blocks for every module with endpoints: Problem Details and the exceptions they render
 * ({@link NotFoundException}, {@link ConflictException}, {@link ValidationException}). */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
@NullMarked
package com.mentorhub.web;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
