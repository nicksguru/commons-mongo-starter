package guru.nicks.commons.mongo;

import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.convert.LazyLoadingProxy;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;

/**
 * Fields (collections too) annotated with both:
 * <ol>
 *  <li>{@link DocumentReference @DocumentReference} (plain field holding a value, recommended by Mongo) or
 *  {@link DBRef @DBRef} (a special document, not recommended by Mongo)</li>
 *  <li>this annotation</li>
 * </ol>
 * are processed by a listener which performs transparent insert/update (but never removal) of the referenced object(s).
 * <p>
 * WARNING:
 * <ol>
 *  <li>Cascade save is incompatible with fields having {@link DocumentReference#lazy()} = {@code true} (for
 *      {@link DBRef#lazy()}, there's no such problem). The issue is: lazy loader replaces field values
 *      a CGLIB proxy which acts in a strange way: if field value is {@code null}, {@code document.getLazyLoadedField()}
 *      <b>never returns {@code null}</b> (it returns the proxy object), but then,
 *      {@code document.getLazyLoadedObject().getSomeNextedField()} raises {@link NullPointerException} because
 *      {@code lazyLoadedObject} is {@code null}.</li>
 *  <li>Cascade save is incompatible with {@link Version @Version} - the error is: <i>Cannot save entity ID with
 *      version VER to collection COL. Has it been modified meanwhile?</i>.
 *  </li>
 *  <li>Getters for properties annotated with {@link DBRef @DBRef}/{@link DocumentReference @DocumentReference}
 *      <b>in lazy mode</b> (which is not the default) always return non-null (in fact
 *      {@link LazyLoadingProxy}) for non-null object references ({@link DocumentReference @DocumentReference} does so
 *      <b>even for null references!</b>) and for collections.<p>
 *      Luckily, lazy collections get loaded when {@link Collection#isEmpty()} is called on them, which means everything
 *      is OK, just null properties become empty collections. But not for single-object references, because
 *      {@link LazyLoadingProxy} loads objects only when underlying object's properties are accessed (not when the
 *      property getter is called). Moreover, <b>lazy properties referring to non-existing objects always load
 *      successfully</b>, just the referenced object returned is empty, which may lead to mission-critical errors.
 *  </li>
 * </ol>
 * Thus, the only safe use case for lazy loading is collections. For one-to-one relationships, prefer non-lazy
 * {@link DocumentReference}, but beware of cyclic references and big fetch graphs.
 *
 * @see MongoCascadeSaveListener
 * @see org.springframework.data.mongodb.core.convert.ReferenceLookupDelegate
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MongoCascadeSave {
}
