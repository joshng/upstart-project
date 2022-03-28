package upstart.util.functions.optics;

import java.util.function.UnaryOperator;

/**
 * A composable/chainable mechanism for building a copy of a graph of immutable objects with a value altered
 * on a (arbitrarily deeply-nested) attribute.<p/>
 *
 * For example, consider the following immutable types:
 * <pre>{@code
 * interface ContactInfo {
 *   Address address();
 *   PhoneNumber phoneNumber();
 *
 *   ContactInfo withAddress(Address newAddress);
 *   ContactInfo withPhoneNumber(PhoneNumber newPhoneNumber);
 * }
 *
 * interface Member {
 *   ContactInfo contactInfo();
 *   Member withContactInfo(ContactInfo newContactInfo);
 * }
 * }</pre>
 *
 *
 * Without using lenses, to update the Address and PhoneNumber for a given Member, we might need to construct the following code:
 * <pre>{@code
 * Member updatedMember = member.withContactInfo(member.contactInfo().withPhoneNumber(newPhoneNumber).withAddress(newAddress));
 * }</pre>
 *
 *
 * <pre>{@code
 * Lens<Member, ContactInfo> memberContactInfo = Lens.of(Member::contactInfo, Member::withContactInfo);
 * Lens<ContactInfo, Address> contactAddress = Lens.of(ContactInfo::address, ContactInfo::withAddress);
 * Lens<ContactInfo, PhoneNumber> contactPhoneNumber = Lens.of(ContactInfo::phoneNumber, ContactInfo::withPhoneNumber);
 *
 * Lens<Member, Address> memberAddress = memberContactInfo.andThen(contactAddress);
 * Lens<Member, PhoneNumber> memberPhoneNumber = memberContactInfo.andThen(contactPhoneNumber);
 *
 * Member newMember = FluentEditor.of(member)
 *         .set(memberAddress, newAddress)
 *         .set(memberPhoneNumber, newPhoneNumber)
 *         .result();
 * }</pre>
 */
public interface Lens<T, V> extends Getter<T, V>, Setter<T, V> {
  static <T, V> Lens<T, V> of(Getter<? super T, ? extends V> getter, Setter<T, ? super V> setter) {
    return new Lens<>() {
      @Override
      public V get(T instance) {
        return getter.get(instance);
      }

      @Override
      public T set(T instance, V value) {
        return setter.set(instance, value);
      }
    };
  }

  default <U> Lens<U, V> compose(Lens<U, T> outer) {
    return outer.andThen(this);
  }

  default <V2> Lens<T, V2> andThen(Lens<V, V2> inner) {
    return of(andThenGet(inner), (instance, value) -> set(instance, inner.set(get(instance), value)));
  }

  default T update(T instance, UnaryOperator<V> updater) {
    return set(instance, updater.apply(get(instance)));
  }

  default <B> Setter<T, UnaryOperator<B>> builderUpdater(BuilderOptics<V, B> builderOptics) {
    return (instance, updater) -> update(instance, value -> builderOptics.update(value, updater));
  }

  default UnaryOperator<T> bind(V value) {
    return instance -> set(instance, value);
  }
}
